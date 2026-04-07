package com.nosuchelements.maven;

import com.nosuchelements.consolidated.ConsolidatedPdfGenerator;
import com.nosuchelements.cucumber.CucumberJsonParser;
import com.nosuchelements.cucumber.model.CucumberFeature;
import com.nosuchelements.cucumber.model.ReportMetadata;
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
 * Maven Mojo — generates Cucumber PDF reports from JSON results.
 *
 * <h3>Report modes</h3>
 * <table border="1" cellpadding="4">
 *   <tr><th>Mode</th><th>Output</th></tr>
 *   <tr><td>{@code split} (default)</td><td>One PDF per feature</td></tr>
 *   <tr><td>{@code consolidated}</td><td>Single PDF — all sections</td></tr>
 *   <tr><td>{@code both}</td><td>Both simultaneously</td></tr>
 * </table>
 *
 * <h3>v1.5.0 additions</h3>
 * <ul>
 *   <li>{@code <metadata>} block — environment / build info on Dashboard</li>
 *   <li>{@code displaySlowTests} — top-N slowest scenarios section</li>
 *   <li>{@code slowTestTopN} — how many slow tests to show (default 15)</li>
 *   <li>Version banner updated to v1.5.0</li>
 * </ul>
 */
@Mojo(name = "generate-pdfs",
      defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST,
      threadSafe = true)
public class SplitPdfReporterMojo extends AbstractMojo {

    static final String VERSION = ConsolidatedPdfGenerator.VERSION;

    // =========================================================================
    // Parameters — input
    // =========================================================================

    @Parameter(property = "cucumberJson",
               defaultValue = "${project.build.directory}/cucumber.json")
    private File cucumberJson;

    @Parameter(property = "cucumberJsonPattern")
    private String cucumberJsonPattern;

    @Parameter(property = "scanRoot",
               defaultValue = "${project.basedir}")
    private File scanRoot;

    // =========================================================================
    // Parameters — output
    // =========================================================================

    @Parameter(property = "reportOutputDir",
               defaultValue = "${project.build.directory}/cucumber-reports")
    private File outputDirectory;

    @Parameter(property = "project.basedir",
               defaultValue = "${project.basedir}", readonly = true)
    private File baseDir;

    // =========================================================================
    // Parameters — report mode
    // =========================================================================

    @Parameter(property = "reportMode", defaultValue = "split")
    private String reportMode;

    // =========================================================================
    // Parameters — consolidated report
    // =========================================================================

    @Parameter(property = "consolidatedReportName",
               defaultValue = "cucumber-report.pdf")
    private String consolidatedReportName;

    @Parameter(property = "reportTitle", defaultValue = "Cucumber Test Report")
    private String reportTitle;

    @Parameter(property = "displayDashboard",      defaultValue = "true")  private boolean displayDashboard;
    @Parameter(property = "displayFeature",        defaultValue = "true")  private boolean displayFeature;
    @Parameter(property = "displayScenario",       defaultValue = "true")  private boolean displayScenario;
    @Parameter(property = "displayDetailed",       defaultValue = "true")  private boolean displayDetailed;
    @Parameter(property = "displayExpanded",       defaultValue = "false") private boolean displayExpanded;
    @Parameter(property = "displayTagStats",       defaultValue = "true")  private boolean displayTagStats;
    @Parameter(property = "displayFailureSummary", defaultValue = "true")  private boolean displayFailureSummary;

    /**
     * Show the Slow Tests section (top-N slowest scenarios sorted by duration).
     * Default: {@code false}.
     */
    @Parameter(property = "displaySlowTests", defaultValue = "false")
    private boolean displaySlowTests;

    /**
     * Number of slowest scenarios to include in the Slow Tests section.
     * Default: {@code 15}.
     */
    @Parameter(property = "slowTestTopN", defaultValue = "15")
    private int slowTestTopN;

    /**
     * Optional environment / build metadata shown on the Dashboard.
     *
     * <pre>{@code
     * <metadata>
     *   <environment>QA</environment>
     *   <branch>${env.GIT_BRANCH}</branch>
     *   <build>${env.BUILD_NUMBER}</build>
     *   <appVersion>2.14.0</appVersion>
     *   <browser>Chrome 124</browser>
     * </metadata>
     * }</pre>
     */
    @Parameter
    private ReportMetadata metadata;

    // =========================================================================
    // Parameters — behaviour
    // =========================================================================

    @Parameter(property = "skipSplitPdfReporter", defaultValue = "false") private boolean skip;
    @Parameter(property = "failOnNoFeatures",     defaultValue = "true")  private boolean failOnNoFeatures;
    @Parameter(property = "verbose",              defaultValue = "false") private boolean verbose;
    @Parameter(property = "consolidate",          defaultValue = "false") private boolean consolidate;
    @Parameter(property = "parallel",             defaultValue = "false") private boolean parallel;

    // =========================================================================
    // Parameters — split page content
    // =========================================================================

    @Parameter(property = "includeSummaryPage",   defaultValue = "true")  private boolean includeSummaryPage;
    @Parameter(property = "includeFeaturePage",   defaultValue = "true")  private boolean includeFeaturePage;
    @Parameter(property = "includeDetailedPages", defaultValue = "true")  private boolean includeDetailedPages;
    @Parameter(property = "maxOutputLines",       defaultValue = "20")    private int maxOutputLines;
    @Parameter(property = "tagPrefix",            defaultValue = "QTEST_TC_") private String tagPrefix;

    @Parameter
    private ColorSchemeConfig colors;

    // =========================================================================
    // Execution
    // =========================================================================

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping Cucumber PDF Reporter (skipSplitPdfReporter=true)");
            return;
        }

        banner();

        String mode = reportMode != null ? reportMode.trim().toLowerCase() : "split";
        if (!mode.equals("split") && !mode.equals("consolidated") && !mode.equals("both")) {
            throw new MojoExecutionException(
                    "Invalid reportMode '" + reportMode
                    + "'. Valid values: split | consolidated | both");
        }

        ColorScheme.apply(colors);

        List<File> jsonFiles = resolveInputFiles();
        if (jsonFiles.isEmpty()) {
            String msg = buildNoFilesMessage();
            if (failOnNoFeatures) {
                throw new MojoExecutionException(msg + "  Set failOnNoFeatures=false to warn only.");
            }
            getLog().warn(msg);
            getLog().warn("Skipping PDF generation.");
            return;
        }

        ensureOutputDirectory();

        List<CucumberFeature> allFeatures = parseAllJsonFiles(jsonFiles);
        if (allFeatures.isEmpty()) {
            getLog().warn("No features found in any input file.");
            return;
        }

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

        getLog().info("");
        getLog().info("=====================================================");
        getLog().info("  Cucumber PDF Reporter v" + VERSION + " — Summary");
        getLog().info("  Mode     : " + mode);
        getLog().info("  Features : " + allFeatures.size());
        getLog().info("  Output   : " + outputDirectory.getAbsolutePath());
        if (!"split".equals(mode)) {
            getLog().info("  Consolidated PDF : " + consolidatedReportName);
        }
        getLog().info("=====================================================");

        int totalFailures = splitFailures + consolidatedFailures;
        if (totalFailures > 0) {
            throw new MojoFailureException(
                    "PDF generation completed with " + totalFailures + " failure(s). See log.");
        }
    }

    // =========================================================================
    // Consolidated generation
    // =========================================================================

    private int generateConsolidatedPdf(List<CucumberFeature> features)
            throws MojoExecutionException {
        String effectiveName = (consolidatedReportName != null
                && !consolidatedReportName.isBlank())
                ? consolidatedReportName.strip()
                : "cucumber-report.pdf";
        String outPath = outputDirectory.getAbsolutePath()
                + File.separator + effectiveName;

        try {
            new ConsolidatedPdfGenerator(
                    displayDashboard, displayFeature, displayScenario,
                    displayDetailed, displayExpanded, displayTagStats,
                    displayFailureSummary, displaySlowTests,
                    maxOutputLines, slowTestTopN,
                    reportTitle, tagPrefix, metadata)
                    .generateReport(features, outPath);
            getLog().info("[OK] Consolidated PDF : " + effectiveName);
            return 0;
        } catch (IOException e) {
            getLog().error("[FAIL] Consolidated PDF: " + e.getMessage(), e);
            return 1;
        }
    }

    // =========================================================================
    // JSON parsing + dedup
    // =========================================================================

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

    // =========================================================================
    // Split PDF generation
    // =========================================================================

    private int[] generateSplitPdfs(List<CucumberFeature> features)
            throws MojoExecutionException {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();
        CucumberJsonParser parser = new CucumberJsonParser(verbose, tagPrefix);

        Stream<CucumberFeature> stream = parallel
                ? features.parallelStream() : features.stream();

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

                new FeaturePdfGenerator(
                        includeSummaryPage, includeFeaturePage,
                        includeDetailedPages, maxOutputLines, tagPrefix)
                        .generateFeaturePdf(feature, outputPath);

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

        if (!errors.isEmpty()) errors.forEach(e -> getLog().error("  " + e));
        return new int[]{ successCount.get(), failureCount.get() };
    }

    // =========================================================================
    // Input file resolution
    // =========================================================================

    private List<File> resolveInputFiles() throws MojoExecutionException {
        if (cucumberJsonPattern != null && !cucumberJsonPattern.isBlank()) {
            return resolveGlob(cucumberJsonPattern,
                    scanRoot != null ? scanRoot : baseDir);
        }
        if (cucumberJson != null && cucumberJson.exists() && cucumberJson.isFile()) {
            return List.of(cucumberJson);
        }
        return List.of();
    }

    private List<File> resolveGlob(String pattern, File root) throws MojoExecutionException {
        Path base = root.toPath();
        PathMatcher m = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
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

    // =========================================================================
    // Helpers
    // =========================================================================

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
                    "Failed to create output directory: "
                    + outputDirectory.getAbsolutePath());
        }
    }

    private void banner() {
        getLog().info("================================================");
        getLog().info("  Cucumber PDF Reporter  v" + VERSION);
        getLog().info("================================================");
        getLog().info("  reportMode           : " + reportMode);
        getLog().info("  cucumberJson         : "
                + (cucumberJson != null ? cucumberJson.getAbsolutePath() : "(none)"));
        getLog().info("  cucumberJsonPattern  : "
                + (cucumberJsonPattern != null ? cucumberJsonPattern : "(none)"));
        getLog().info("  scanRoot             : "
                + (scanRoot != null ? scanRoot.getAbsolutePath()
                                    : baseDir.getAbsolutePath()));
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
        getLog().info("  displaySlowTests     : " + displaySlowTests);
        getLog().info("  slowTestTopN         : " + slowTestTopN);
        getLog().info("  failOnNoFeatures     : " + failOnNoFeatures);
        getLog().info("  maxOutputLines       : " + maxOutputLines);
        getLog().info("  tagPrefix            : " + tagPrefix);
        getLog().info("  metadata configured  : " + (metadata != null && !metadata.isEmpty()));
        getLog().info("  colors configured    : " + (colors != null));
        getLog().info("================================================");
    }
}
