package com.nosuchelements.consolidated;

import com.nosuchelements.consolidated.sections.*;
import com.nosuchelements.cucumber.model.CucumberFeature;
import com.nosuchelements.cucumber.model.ReportMetadata;
import com.nosuchelements.pdf.ColorScheme;
import com.nosuchelements.pdf.PdfStyler;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Generates the consolidated single-PDF report from all parsed Cucumber features.
 *
 * <h3>Section order</h3>
 * <pre>
 *   [1]  DashboardSection       — metrics, bars, at-a-glance, metadata block
 *   [2]  FailureSummarySection  — CI triage: failing scenarios + errors
 *   [3]  SlowTestsSection       — top-N slowest scenarios  (opt-in)
 *   [4]  FeaturesSection        — full feature table with Case ID column
 *   [5]  ScenariosSection       — all scenarios with Tags column
 *   [6]  TagStatsSection        — per-tag pass/fail/skip breakdown
 *   [7]  DetailedSection        — step-by-step: errors, tables, docstrings, screenshots
 *   [8]  ExpandedSection        — screenshots only (opt-in)
 * </pre>
 *
 * <h3>v1.5.0 additions</h3>
 * <ul>
 *   <li>{@link ReportMetadata} — environment/build info block on Dashboard</li>
 *   <li>{@link SlowTestsSection} — top-N slowest scenario table ({@code displaySlowTests})</li>
 *   <li>PDF document properties (title, subject, creator) now set</li>
 *   <li>Version string centralised to {@code VERSION} constant</li>
 *   <li>All section footers updated to v1.5.0</li>
 * </ul>
 */
public class ConsolidatedPdfGenerator {

    private static final Logger log = LoggerFactory.getLogger(ConsolidatedPdfGenerator.class);

    public static final String VERSION = "1.5.0";

    // ---- Section display flags -----------------------------------------------
    private final boolean displayDashboard;
    private final boolean displayFeatures;
    private final boolean displayScenarios;
    private final boolean displayDetailed;
    private final boolean displayExpanded;
    private final boolean displayTagStats;
    private final boolean displayFailureSummary;
    private final boolean displaySlowTests;
    private final int     maxOutputLines;
    private final int     slowTestTopN;
    private final String  reportTitle;
    private final String  tagPrefix;
    private final ReportMetadata metadata;

    // =========================================================================
    // Constructors — backward-compatible chain
    // =========================================================================

    /** All sections on, expanded + slowTests off. */
    public ConsolidatedPdfGenerator() {
        this(true, true, true, true, false, true, true, false,
                20, 15, "Cucumber Test Report", "QTEST_TC_", null);
    }

    /** Legacy 8-arg (pre-v1.2.0). */
    public ConsolidatedPdfGenerator(
            boolean displayDashboard, boolean displayFeatures,
            boolean displayScenarios, boolean displayDetailed,
            boolean displayExpanded,
            int maxOutputLines, String reportTitle, String tagPrefix) {
        this(displayDashboard, displayFeatures, displayScenarios, displayDetailed,
             displayExpanded, true, true, false,
             maxOutputLines, 15, reportTitle, tagPrefix, null);
    }

    /** Legacy 9-arg (added displayTagStats in v1.2.0). */
    public ConsolidatedPdfGenerator(
            boolean displayDashboard, boolean displayFeatures,
            boolean displayScenarios, boolean displayDetailed,
            boolean displayExpanded, boolean displayTagStats,
            int maxOutputLines, String reportTitle, String tagPrefix) {
        this(displayDashboard, displayFeatures, displayScenarios, displayDetailed,
             displayExpanded, displayTagStats, true, false,
             maxOutputLines, 15, reportTitle, tagPrefix, null);
    }

    /** Legacy 10-arg (added displayFailureSummary in v1.2.0). */
    public ConsolidatedPdfGenerator(
            boolean displayDashboard, boolean displayFeatures,
            boolean displayScenarios, boolean displayDetailed,
            boolean displayExpanded, boolean displayTagStats,
            boolean displayFailureSummary,
            int maxOutputLines, String reportTitle, String tagPrefix) {
        this(displayDashboard, displayFeatures, displayScenarios, displayDetailed,
             displayExpanded, displayTagStats, displayFailureSummary, false,
             maxOutputLines, 15, reportTitle, tagPrefix, null);
    }

    /** Full 13-arg constructor — used by updated Mojo. */
    public ConsolidatedPdfGenerator(
            boolean displayDashboard, boolean displayFeatures,
            boolean displayScenarios, boolean displayDetailed,
            boolean displayExpanded, boolean displayTagStats,
            boolean displayFailureSummary, boolean displaySlowTests,
            int maxOutputLines, int slowTestTopN,
            String reportTitle, String tagPrefix,
            ReportMetadata metadata) {
        this.displayDashboard      = displayDashboard;
        this.displayFeatures       = displayFeatures;
        this.displayScenarios      = displayScenarios;
        this.displayDetailed       = displayDetailed;
        this.displayExpanded       = displayExpanded;
        this.displayTagStats       = displayTagStats;
        this.displayFailureSummary = displayFailureSummary;
        this.displaySlowTests      = displaySlowTests;
        this.maxOutputLines        = Math.max(1, maxOutputLines);
        this.slowTestTopN          = Math.max(1, Math.min(slowTestTopN, 50));
        this.reportTitle           = (reportTitle != null && !reportTitle.isBlank())
                ? reportTitle : "Cucumber Test Report";
        this.tagPrefix             = (tagPrefix != null && !tagPrefix.isBlank())
                ? tagPrefix.strip() : "QTEST_TC_";
        this.metadata              = metadata;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public void generateReport(List<CucumberFeature> features,
                                String outputFilePath) throws IOException {
        log.info("Generating consolidated PDF ({} features) -> {}",
                features.size(), outputFilePath);

        File outFile = new File(outputFilePath);
        File parent  = outFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create output directory: " + parent);
        }

        ReportStats stats  = ReportStats.compute(features);
        PdfStyler   styler = new PdfStyler();
        TableOfContents toc = new TableOfContents();

        try (PDDocument doc = new PDDocument()) {
            // Set PDF document properties
            var info = doc.getDocumentInformation();
            info.setTitle(reportTitle);
            info.setSubject("Cucumber Test Report");
            info.setCreator("Cucumber PDF Reporter v" + VERSION);
            if (metadata != null && metadata.getEnvironment() != null) {
                info.setCustomMetadataValue("Environment", metadata.getEnvironment());
            }

            // [1] Dashboard
            if (displayDashboard) {
                PDPage pg = addPage(doc);
                new DashboardSection(styler, reportTitle, tagPrefix, metadata)
                        .build(doc, pg, features, stats, toc);
            }

            // [2] Failure Summary
            if (displayFailureSummary) {
                PDPage pg = addPage(doc);
                new FailureSummarySection(styler, maxOutputLines)
                        .build(doc, pg, features, toc);
            }

            // [3] Slow Tests (v1.5.0)
            if (displaySlowTests) {
                PDPage pg = addPage(doc);
                new SlowTestsSection(styler, slowTestTopN)
                        .build(doc, pg, features);
            }

            // [4] Features table
            if (displayFeatures) {
                PDPage pg = addPage(doc);
                new FeaturesSection(styler, tagPrefix)
                        .build(doc, pg, features, toc);
            }

            // [5] Scenarios
            if (displayScenarios) {
                PDPage pg = addPage(doc);
                new ScenariosSection(styler)
                        .build(doc, pg, features, toc);
            }

            // [6] Tag statistics
            if (displayTagStats) {
                PDPage pg = addPage(doc);
                new TagStatsSection(styler)
                        .build(doc, pg, features, toc);
            }

            // [7] Detailed steps
            if (displayDetailed) {
                PDPage pg = addPage(doc);
                new DetailedSection(styler, maxOutputLines)
                        .build(doc, pg, features, toc);
            }

            // [8] Expanded screenshots
            if (displayExpanded) {
                PDPage pg = addPage(doc);
                new ExpandedSection(styler, maxOutputLines)
                        .build(doc, pg, features, toc);
            }

            stampPageNumbers(doc, styler);

            doc.save(outFile);
            log.info("Consolidated PDF saved ({} pages): {}",
                    doc.getNumberOfPages(), outputFilePath);
        }
    }

    // =========================================================================
    // Page number stamp (second pass)
    // =========================================================================

    public static void stampPageNumbers(PDDocument doc, PdfStyler styler) throws IOException {
        int total = doc.getNumberOfPages();
        for (int i = 0; i < total; i++) {
            PDPage page  = doc.getPage(i);
            String label = "Page " + (i + 1) + " of " + total;
            float  x     = ConsolidatedPageCursor.PAGE_W
                    - ConsolidatedPageCursor.MARGIN_H
                    - label.length() * 4.5f;
            try (PDPageContentStream cs = new PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
                styler.drawText(doc, cs, label,
                        x, 14f, styler.regularFont(), 7f, ColorScheme.TEXT_HINT);
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PDPage addPage(PDDocument doc) {
        PDPage pg = new PDPage(PDRectangle.A4);
        doc.addPage(pg);
        return pg;
    }
}
