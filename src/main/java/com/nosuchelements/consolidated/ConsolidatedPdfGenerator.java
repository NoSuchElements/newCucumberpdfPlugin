package com.nosuchelements.consolidated;

import com.nosuchelements.consolidated.sections.*;
import com.nosuchelements.cucumber.model.CucumberFeature;
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
 *   [1]   DashboardSection       — metric cards, bars, features-at-a-glance
 *   [2]   FailureSummarySection  — CI triage: every failing scenario with errors
 *   [3]   FeaturesSection        — full feature table with Case ID column
 *   [4]   ScenariosSection       — all scenarios with Tags column
 *   [5]   TagStatsSection        — per-tag pass/fail/skip breakdown
 *   [6]   DetailedSection        — step-by-step with errors, tables, docstrings
 *   [7]   ExpandedSection        — screenshots and attachments (opt-in)
 * </pre>
 *
 * <h3>Two-pass page numbering</h3>
 * <p>PDFBox does not allow forward references, so page numbers are stamped in a
 * second pass after all content is written.  A {@link PageNumberRegistry} is
 * threaded through every section cursor; after the document is fully built the
 * generator iterates all pages and stamps "Page N of T" in the footer.</p>
 *
 * <h3>Content-block sharing</h3>
 * <p>Error blocks, log blocks, data tables, DocStrings, and screenshots are all
 * rendered through the shared {@link ContentBlockRenderer} — there is no
 * duplicated drawing code between sections.</p>
 */
public class ConsolidatedPdfGenerator {

    private static final Logger log = LoggerFactory.getLogger(ConsolidatedPdfGenerator.class);

    // ---- Section display flags ----
    private final boolean displayDashboard;
    private final boolean displayFeatures;
    private final boolean displayScenarios;
    private final boolean displayDetailed;
    private final boolean displayExpanded;
    private final boolean displayTagStats;
    private final boolean displayFailureSummary;
    private final int     maxOutputLines;
    private final String  reportTitle;
    private final String  tagPrefix;

    /** All sections on, expanded off. */
    public ConsolidatedPdfGenerator() {
        this(true, true, true, true, false, true, true, 20,
                "Cucumber Test Report", "QTEST_TC_");
    }

    /** Legacy 8-arg constructor. */
    public ConsolidatedPdfGenerator(
            boolean displayDashboard, boolean displayFeatures,
            boolean displayScenarios, boolean displayDetailed,
            boolean displayExpanded,
            int maxOutputLines, String reportTitle, String tagPrefix) {
        this(displayDashboard, displayFeatures, displayScenarios, displayDetailed,
             displayExpanded, true, true, maxOutputLines, reportTitle, tagPrefix);
    }

    /** Legacy 9-arg constructor (added displayTagStats). */
    public ConsolidatedPdfGenerator(
            boolean displayDashboard, boolean displayFeatures,
            boolean displayScenarios, boolean displayDetailed,
            boolean displayExpanded, boolean displayTagStats,
            int maxOutputLines, String reportTitle, String tagPrefix) {
        this(displayDashboard, displayFeatures, displayScenarios, displayDetailed,
             displayExpanded, displayTagStats, true, maxOutputLines, reportTitle, tagPrefix);
    }

    /** Full 10-arg constructor — invoked by the updated Mojo. */
    public ConsolidatedPdfGenerator(
            boolean displayDashboard, boolean displayFeatures,
            boolean displayScenarios, boolean displayDetailed,
            boolean displayExpanded, boolean displayTagStats,
            boolean displayFailureSummary,
            int maxOutputLines, String reportTitle, String tagPrefix) {
        this.displayDashboard      = displayDashboard;
        this.displayFeatures       = displayFeatures;
        this.displayScenarios      = displayScenarios;
        this.displayDetailed       = displayDetailed;
        this.displayExpanded       = displayExpanded;
        this.displayTagStats       = displayTagStats;
        this.displayFailureSummary = displayFailureSummary;
        this.maxOutputLines        = Math.max(1, maxOutputLines);
        this.reportTitle           = (reportTitle != null && !reportTitle.isBlank())
                ? reportTitle : "Cucumber Test Report";
        this.tagPrefix             = (tagPrefix   != null && !tagPrefix.isBlank())
                ? tagPrefix.strip() : "QTEST_TC_";
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Build the consolidated PDF and write it to {@code outputFilePath}.
     *
     * @param features      non-null list of parsed Cucumber features
     * @param outputFilePath absolute path for the output file
     */
    public void generateReport(List<CucumberFeature> features,
                                String outputFilePath) throws IOException {

        log.info("Generating consolidated PDF ({} features) -> {}",
                features.size(), outputFilePath);

        // Ensure parent directory exists
        File outFile = new File(outputFilePath);
        File parent  = outFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create output directory: " + parent);
        }

        // Pre-compute aggregate statistics (used by Dashboard)
        ReportStats stats = ReportStats.compute(features);

        PdfStyler styler = new PdfStyler();

        // TOC collects section → page-number anchors
        TableOfContents toc = new TableOfContents();

        try (PDDocument doc = new PDDocument()) {

            // ---- [1] Dashboard ----
            if (displayDashboard) {
                PDPage pg = addPage(doc);
                new DashboardSection(styler, reportTitle, tagPrefix)
                        .build(doc, pg, features, stats, toc);
            }

            // ---- [1b] Failure Summary (placed early — CI triage page) ----
            if (displayFailureSummary) {
                PDPage pg = addPage(doc);
                new FailureSummarySection(styler, maxOutputLines)
                        .build(doc, pg, features, toc);
            }

            // ---- [2] Features table ----
            if (displayFeatures) {
                PDPage pg = addPage(doc);
                new FeaturesSection(styler, tagPrefix)
                        .build(doc, pg, features, toc);
            }

            // ---- [3] Scenarios ----
            if (displayScenarios) {
                PDPage pg = addPage(doc);
                new ScenariosSection(styler)
                        .build(doc, pg, features, toc);
            }

            // ---- [3b] Tag statistics ----
            if (displayTagStats) {
                PDPage pg = addPage(doc);
                new TagStatsSection(styler)
                        .build(doc, pg, features, toc);
            }

            // ---- [4] Detailed steps ----
            if (displayDetailed) {
                PDPage pg = addPage(doc);
                new DetailedSection(styler, maxOutputLines)
                        .build(doc, pg, features, toc);
            }

            // ---- [5] Expanded (screenshots) ----
            if (displayExpanded) {
                PDPage pg = addPage(doc);
                new ExpandedSection(styler, maxOutputLines)
                        .build(doc, pg, features, toc);
            }

            // ---- Second pass: stamp "Page N of T" on every page ----
            stampPageNumbers(doc, styler);

            doc.save(outFile);
            log.info("Consolidated PDF saved ({} pages): {}",
                    doc.getNumberOfPages(), outputFilePath);
        }
    }

    // -----------------------------------------------------------------------
    // Page number stamping  (second pass)
    // -----------------------------------------------------------------------

    /**
     * Iterate every page in the finished document and stamp a "Page N of T" label
     * in the bottom-right footer area.
     *
     * <p>Called after <em>all</em> sections have been written so the total page
     * count is known.</p>
     */
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

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static PDPage addPage(PDDocument doc) {
        PDPage pg = new PDPage(PDRectangle.A4);
        doc.addPage(pg);
        return pg;
    }
}
