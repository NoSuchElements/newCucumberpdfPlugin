package com.nosuchelements.maven;

import com.nosuchelements.consolidated.ConsolidatedPdfGenerator;
import com.nosuchelements.cucumber.CucumberJsonParser;
import com.nosuchelements.cucumber.model.CucumberFeature;
import com.nosuchelements.pdf.ColorScheme;
import com.nosuchelements.pdf.ColorSchemeConfig;
import com.nosuchelements.pdf.FeaturePdfGenerator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Maven Mojo that generates Cucumber PDF reports from JSON results.
 *
 * <p>Three report modes are supported, controlled by {@link #reportMode}:</p>
 *
 * <table border="1" cellpadding="4">
 *   <tr><th>Mode</th><th>Output</th></tr>
 *   <tr>
 *     <td>{@code split} (default)</td>
 *     <td>One PDF per Cucumber feature (original behaviour)</td>
 *   </tr>
 *   <tr>
 *     <td>{@code consolidated}</td>
 *     <td>One single PDF combining Dashboard → Features → Scenarios →
 *         Detailed [→ Expanded] sections, matching the grasshopper7 layout</td>
 *   </tr>
 *   <tr>
 *     <td>{@code both}</td>
 *     <td>Generates both the consolidated PDF <em>and</em> all per-feature split PDFs</td>
 *   </tr>
 * </table>
 *
 * <h3>Minimal split configuration (unchanged)</h3>
 * <pre>{@code
 * <configuration>
 *   <cucumberJson>${project.build.directory}/cucumber.json</cucumberJson>
 *   <reportOutputDir>${project.build.directory}/cucumber-reports</reportOutputDir>
 * </configuration>
 * }</pre>
 *
 * <h3>Consolidated report</h3>
 * <pre>{@code
 * <configuration>
 *   <reportMode>consolidated</reportMode>
 *   <cucumberJsonPattern>**\/cucumber*.json</cucumberJsonPattern>
 *   <consolidatedReportName>TestReport.pdf</consolidatedReportName>
 *   <reportTitle>Regression Suite — Sprint 42</reportTitle>
 *   <displayExpanded>true</displayExpanded>
 * </configuration>
 * }</pre>
 *
 * <h3>Both modes</h3>
 * <pre>{@code
 * <configuration>
 *   <reportMode>both</reportMode>
 *   <reportTitle>My Project Test Run</reportTitle>
 * </configuration>
 * }</pre>
 */
@Mojo(name = "generate-pdfs",
        defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST,
        threadSafe = true)
public class SplitPdfReporterMojo extends AbstractMojo {

    // -----------------------------------------------------------------------
    // Parameters — input discovery
    // -----------------------------------------------------------------------

    /** Path to a single Cucumber JSON results file. */
    @Parameter(property = "cucumberJson",
               defaultValue = "${project.build.directory}/cucumber.json")
    private File cucumberJson;

    /**
     * Ant-style glob pattern for locating one or more Cucumber JSON files.
     * Overrides {@link #cucumberJson} when non-blank.
     */
    @Parameter(property = "cucumberJsonPattern")
    private String cucumberJsonPattern;

    /**
     * F-13: Root directory from which {@link #cucumberJsonPattern} is applied.
     * Set to {@code ${project.basedir}/..} to scan across sibling submodules.
     */
    @Parameter(property = "scanRoot",
               defaultValue = "${project.basedir}")
    private File scanRoot;

    // -----------------------------------------------------------------------
    // Parameters — output
    // -----------------------------------------------------------------------

    /** Directory where PDF files are written. */
    @Parameter(property = "reportOutputDir",
               defaultValue = "${project.build.directory}/cucumber-reports")
    private File outputDirectory;

    /** Base directory (read-only, injected by Maven). */
    @Parameter(property = "project.basedir",
               defaultValue = "${project.basedir}",
               readonly = true)
    private File baseDir;

    // -----------------------------------------------------------------------
    // Parameters — report mode  (NEW)
    // -----------------------------------------------------------------------

    /**
     * Controls which PDF(s) are generated.
     *
     * <ul>
     *   <li>{@code split}        — one PDF per feature (default, original behaviour)</li>
     *   <li>{@code consolidated} — single PDF with Dashboard/Features/Scenarios/Detailed</li>
     *   <li>{@code both}         — generates both</li>
     * </ul>
     */
    @Parameter(property = "reportMode", defaultValue = "split")
    private String reportMode;

    // -----------------------------------------------------------------------
    // Parameters — consolidated report settings  (NEW)
    // -----------------------------------------------------------------------

    /**
     * File name for the consolidated PDF (written inside {@link #outputDirectory}).
     * Default: {@code cucumber-report.pdf}
     */
    @Parameter(property = "consolidatedReportName",
               defaultValue = "cucumber-report.pdf")
    private String consolidatedReportName;

    /**
     * Title shown on the Dashboard page of the consolidated report.
     * Default: {@code "Cucumber Test Report"}
     */
    @Parameter(property = "reportTitle", defaultValue = "Cucumber Test Report")
    private String reportTitle;

    /** Show the Dashboard section in the consolidated report (default: true). */
    @Parameter(property = "displayDashboard", defaultValue = "true")
    private boolean displayDashboard;

    /** Show the Features table section in the consolidated report (default: true). */
    @Parameter(property = "displayFeature", defaultValue = "true")
    private boolean displayFeature;

    /** Show the Scenarios table section in the consolidated report (default: true). */
    @Parameter(property = "displayScenario", defaultValue = "true")
    private boolean displayScenario;

    /** Show the Detailed steps section in the consolidated report (default: true). */
    @Parameter(property = "displayDetailed", defaultValue = "true")
    private boolean displayDetailed;

    /**
     * Show the Expanded section (screenshots, docstrings, tables) in the
     * consolidated report.  Default: {@code false} to keep file size manageable.
     */
    @Parameter(property = "displayExpanded", defaultValue = "false")
    private boolean displayExpanded;

    /**
     * Show the Tag Statistics section in the consolidated report.
     * Default: {@code true}.
     */
    @Parameter(property = "displayTagStats", defaultValue = "true")
    private boolean displayTagStats;

    /**
     * Show the Failure Summary section in the consolidated report.
     *
     * <p>When {@code true}, a dedicated CI-triage page is inserted right after
     * the Dashboard, listing every FAILED and SKIPPED scenario with its full
     * step detail and error messages. If all scenarios passed, a green
     * "All scenarios passed" banner is shown instead.</p>
     *
     * <p>Default: {@code true} — this is the most actionable section for
     * automated CI pipelines.</p>
     */
    @Parameter(property = "displayFailureSummary", defaultValue = "true")
    private boolean displayFailureSummary;

    // -----------------------------------------------------------------------
    // Parameters — behaviour
    // -----------------------------------------------------------------------

    /** Skip plugin execution entirely. */
    @Parameter(property = "skipSplitPdfReporter", defaultValue = "false")
    private boolean skip;

    /** Fail the build if no Cucumber JSON files are found. */
    @Parameter(property = "failOnNoFeatures", defaultValue = "true")
    private boolean failOnNoFeatures;

    /** Extra debug logging per feature/file. */
    @Parameter(property = "verbose", defaultValue = "false")
    private boolean verbose;

    /**
     * F-13: When {@code true}, deduplicate features by URI across all matched files.
     */
    @Parameter(property = "consolidate", defaultValue = "false")
    private boolean consolidate;

    /**
     * F-15: Generate split PDFs in parallel using a Java parallel stream.
     */
    @Parameter(property = "parallel", defaultValue = "false")
    private boolean parallel;

    // -----------------------------------------------------------------------
    // Parameters — page content
    // -----------------------------------------------------------------------

    /** Accepted but ignored — SummaryPage was removed in v1.1.1. */
    @Parameter(property = "includeSummaryPage", defaultValue = "true")
    private boolean includeSummaryPage;

    @Parameter(property = "includeFeaturePage", defaultValue = "true")
    private boolean includeFeaturePage;

    @Parameter(property = "includeDetailedPages", defaultValue = "true")
    private boolean includeDetailedPages;

    /**
     * F-04: Maximum output log lines per step/hook section.
     * Applies to both split and consolidated reports.
     */
    @Parameter(property = "maxOutputLines", defaultValue = "20")
    private int maxOutputLines;

    /**
     * Tag prefix used to identify the test case ID tag.
     */
    @Parameter(property = "tagPrefix", defaultValue = "QTEST_TC_")
    private String tagPrefix;

    /** F-10: Optional colour overrides. */
    @Parameter
    private ColorSchemeConfig colors;

    // -----------------------------------------------------------------------
    // Execution
    // -----------------------------------------------------------------------

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping Cucumber PDF Reporter (skipSplitPdfReporter=true)");
            return;
        }

        banner();

        // Validate reportMode
        String mode = reportMode != null ? reportMode.trim().toLowerCase() : "split";
        if (!mode.equals("split") && !mode.equals("consolidated") && !mode.equals("both")) {
            throw new MojoExecutionException(
                    "Invalid reportMode '" + reportMode
                    + "'. Valid values: split | consolidated | both");
        }

        // F-10: apply colour overrides
        ColorScheme.apply(colors);

        // Collect and parse all JSON input files
        List<File> jsonFiles = resolveInputFiles();

        if (jsonFiles.isEmpty()) {
            String msg = buildNoFilesMessage();
            if (failOnNoFeatures) {
                throw new MojoExecutionException(
                        msg + "  Set failOnNoFeatures=false to downgrade to a warning.");
            }
            getLog().warn(msg);
            getLog().warn("Skipping PDF generation.");
            return;
        }

        ensureOutputDirectory();

        // Parse all JSON files (with optional URI deduplication)
        List<CucumberFeature> allFeatures = parseAllJsonFiles(jsonFiles);

        if (allFeatures.isEmpty()) {
            getLog().warn("No features found in any input file.");
            return;
        }

        // Generate based on mode
        int splitFailures = 0;

        if ("split".equals(mode) || "both".equals(mode)) {
            getLog().info("Generating split PDFs (" + allFeatures.size() + " feature(s)"
                    + (parallel ? " [parallel]" : "") + ")...");
            int[] counts = generateSplitPdfs(allFeatures);
            getLog().info("[Split]  Success: " + counts[0] + "  Failures: " + counts[1]);
            splitFailures = counts[1];
        }

        int consolidatedFailures = 0;
        if ("consolidated".equals(mode) || "both".equals(mode)) {
            getLog().info("Generating consolidated PDF...");
            consolidatedFailures = generateConsolidatedPdf(allFeatures);
        }

        // Summary
        getLog().info("");
        getLog().info("=====================================================");
        getLog().info("  Cucumber PDF Reporter — Generation Summary");
        getLog().info("  Mode      : " + mode);
        getLog().info("  Features  : " + allFeatures.size());
        getLog().info("  Output    : " + outputDirectory.getAbsolutePath());
        if ("consolidated".equals(mode) || "both".equals(mode)) {
            getLog().info("  Consolidated PDF : " + consolidatedReportName);
        }
        getLog().info("=====================================================");

        int totalFailures = splitFailures + consolidatedFailures;
        if (totalFailures > 0) {
            throw new MojoFailureException(
                    "PDF generation completed with " + totalFailures + " failure(s). See log above.");
        }
    }

    // -----------------------------------------------------------------------
    // Consolidated generation
    // -----------------------------------------------------------------------

    private int generateConsolidatedPdf(List<CucumberFeature> features)
            throws MojoExecutionException {
        // Guard: if user set consolidatedReportName to blank, use the default filename
        String effectiveName = (consolidatedReportName != null && !consolidatedReportName.isBlank())
                ? consolidatedReportName.strip()
                : "cucumber-report.pdf";
        String outPath = outputDirectory.getAbsolutePath()
                + File.separator + effectiveName;

        try {
            ConsolidatedPdfGenerator generator = new ConsolidatedPdfGenerator(
                    displayDashboard,
                    displayFeature,
                    displayScenario,
                    displayDetailed,
                    displayExpanded,
                    displayTagStats,
                    displayFailureSummary,
                    maxOutputLines,
                    reportTitle,
                    tagPrefix);
            generator.generateReport(features, outPath);
            getLog().info("[OK] Consolidated PDF : " + effectiveName);
            return 0;
        } catch (IOException e) {
            getLog().error("[FAIL] Consolidated PDF: " + e.getMessage(), e);
            return 1;
        }
    }

    // -----------------------------------------------------------------------
    // F-13: Parse + deduplicate
    // -----------------------------------------------------------------------

    private List<CucumberFeature> parseAllJsonFiles(List<File> jsonFiles)
            throws MojoExecutionException {
        CucumberJsonParser parser = new CucumberJsonParser(verbose, tagPrefix);
        Map<String, CucumberFeature> byUri = new LinkedHashMap<>();

        for (File jsonFile : jsonFiles) {
            getLog().info("Input : " + jsonFile.getAbsolutePath());
            List<CucumberFeature> features;
            try {
                features = parser.parseJsonFile(jsonFile.getAbsolutePath());
            } catch (IOException e) {
                getLog().error("Failed to parse " + jsonFile.getName()
                        + ": " + e.getMessage(), e);
                continue;
            }

            if (features == null || features.isEmpty()) {
                getLog().warn("No features in: " + jsonFile.getName());
                continue;
            }

            getLog().info("  Parsed " + features.size()
                    + " feature(s) from " + jsonFile.getName());

            for (CucumberFeature f : features) {
                String uri = f.getUri() != null ? f.getUri() : f.getName();
                if (consolidate && byUri.containsKey(uri)) {
                    getLog().info("  [dedup] Replacing earlier run of '" + uri + "'");
                }
                byUri.put(uri, f);
            }
        }
        return new ArrayList<>(byUri.values());
    }

    // -----------------------------------------------------------------------
    // F-15: Split PDF generation (sequential or parallel)
    // -----------------------------------------------------------------------

    private int[] generateSplitPdfs(List<CucumberFeature> features)
            throws MojoExecutionException {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();
        CucumberJsonParser parser = new CucumberJsonParser(verbose, tagPrefix);

        Stream<CucumberFeature> stream = parallel
                ? features.parallelStream()
                : features.stream();

        stream.forEach(feature -> {
            try {
                String qtestCaseId = parser.extractQtestCaseId(feature);
                String filename    = FeaturePdfGenerator.generateFilename(feature, qtestCaseId);
                String outputPath  = outputDirectory.getAbsolutePath()
                        + File.separator + filename;

                if (verbose) {
                    getLog().info("  -> Feature : " + feature.getName());
                    getLog().info("  -> File    : " + filename);
                }

                FeaturePdfGenerator generator = new FeaturePdfGenerator(
                        includeSummaryPage, includeFeaturePage,
                        includeDetailedPages, maxOutputLines, tagPrefix);
                generator.generateFeaturePdf(feature, outputPath);

                getLog().info("[OK] " + filename);
                successCount.incrementAndGet();

            } catch (IOException e) {
                String name = feature.getName() != null ? feature.getName() : "<unnamed>";
                String msg  = "[FAIL] " + name + " — " + e.getMessage();
                getLog().error(msg, e);
                errors.add(msg);
                failureCount.incrementAndGet();
            }
        });

        if (!errors.isEmpty()) {
            errors.forEach(e -> getLog().error("  " + e));
        }
        return new int[]{ successCount.get(), failureCount.get() };
    }

    // -----------------------------------------------------------------------
    // Input file resolution
    // -----------------------------------------------------------------------

    private List<File> resolveInputFiles() throws MojoExecutionException {
        if (cucumberJsonPattern != null && !cucumberJsonPattern.isBlank()) {
            File root = (scanRoot != null) ? scanRoot : baseDir;
            return resolveGlob(cucumberJsonPattern, root);
        }
        if (cucumberJson != null && cucumberJson.exists() && cucumberJson.isFile()) {
            return List.of(cucumberJson);
        }
        return List.of();
    }

    private List<File> resolveGlob(String pattern, File root) throws MojoExecutionException {
        Path base       = root.toPath();
        PathMatcher m   = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        List<File> found = new ArrayList<>();
        try {
            Files.walkFileTree(base, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (m.matches(base.relativize(file))) found.add(file.toFile());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Error scanning for Cucumber JSON: " + e.getMessage(), e);
        }
        if (verbose || found.size() > 1) {
            getLog().info("Pattern '" + pattern + "' matched "
                    + found.size() + " file(s) under " + base);
        }
        return found;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String buildNoFilesMessage() {
        if (cucumberJsonPattern != null) {
            return "No Cucumber JSON file(s) found matching pattern: " + cucumberJsonPattern;
        }
        return "No Cucumber JSON file found at: "
                + (cucumberJson != null ? cucumberJson.getAbsolutePath() : "(null)");
    }

    private void ensureOutputDirectory() throws MojoExecutionException {
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw new MojoExecutionException(
                    "Failed to create output directory: " + outputDirectory.getAbsolutePath());
        }
    }

    private void banner() {
        getLog().info("================================================");
        getLog().info("  Cucumber PDF Reporter  v1.1.6");
        getLog().info("================================================");
        getLog().info("  reportMode           : " + reportMode);
        getLog().info("  cucumberJson         : "
                + (cucumberJson != null ? cucumberJson.getAbsolutePath() : "(none)"));
        getLog().info("  cucumberJsonPattern  : "
                + (cucumberJsonPattern != null ? cucumberJsonPattern : "(none)"));
        getLog().info("  scanRoot             : "
                + (scanRoot != null ? scanRoot.getAbsolutePath() : baseDir.getAbsolutePath()));
        getLog().info("  outputDirectory      : " + outputDirectory.getAbsolutePath());
        getLog().info("  consolidate (dedup)  : " + consolidate);
        getLog().info("  parallel (split)     : " + parallel);
        getLog().info("  reportTitle          : " + reportTitle);
        if (!"split".equalsIgnoreCase(reportMode)) {
            getLog().info("  consolidatedReport   : " + consolidatedReportName);
        }
        getLog().info("  displayDashboard     : " + displayDashboard);
        getLog().info("  displayFeature       : " + displayFeature);
        getLog().info("  displayScenario      : " + displayScenario);
        getLog().info("  displayDetailed      : " + displayDetailed);
        getLog().info("  displayExpanded      : " + displayExpanded);
        getLog().info("  displayTagStats      : " + displayTagStats);
        getLog().info("  displayFailureSummary: " + displayFailureSummary);
        getLog().info("  failOnNoFeatures     : " + failOnNoFeatures);
        getLog().info("  maxOutputLines       : " + maxOutputLines);
        getLog().info("  tagPrefix            : " + tagPrefix);
        getLog().info("  colors configured    : " + (colors != null));
        getLog().info("================================================");
    }
}
