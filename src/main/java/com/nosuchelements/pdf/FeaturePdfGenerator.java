package com.nosuchelements.pdf;

import com.nosuchelements.cucumber.model.CucumberFeature;
import com.nosuchelements.cucumber.model.CucumberScenario;
import com.nosuchelements.cucumber.model.CucumberStep;
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
 * Generates a single per-feature PDF (split mode).
 *
 * <p>This is the original split-mode generator. It produces one PDF per
 * Cucumber feature file, named using the feature name and optional qTest
 * case ID tag.</p>
 */
public class FeaturePdfGenerator {

    private static final Logger log = LoggerFactory.getLogger(FeaturePdfGenerator.class);

    private static final float M   = 36f;
    private static final float PW  = PDRectangle.A4.getWidth();
    private static final float PH  = PDRectangle.A4.getHeight();
    private static final float CW  = PW - 2 * M;
    private static final float LH  = 14f;

    private final boolean includeSummaryPage;
    private final boolean includeFeaturePage;
    private final boolean includeDetailedPages;
    private final int     maxOutputLines;
    private final String  tagPrefix;
    private final PdfStyler s = new PdfStyler();


    /** Default constructor with all sections enabled. */
    public FeaturePdfGenerator() {
        this(true, true, true, 20, "QTEST_TC_");
    }

    public FeaturePdfGenerator(boolean includeSummaryPage,
                                boolean includeFeaturePage,
                                boolean includeDetailedPages,
                                int maxOutputLines,
                                String tagPrefix) {
        this.includeSummaryPage  = includeSummaryPage;
        this.includeFeaturePage  = includeFeaturePage;
        this.includeDetailedPages = includeDetailedPages;
        this.maxOutputLines      = Math.max(1, maxOutputLines);
        this.tagPrefix           = tagPrefix != null ? tagPrefix : "QTEST_TC_";
    }

    // -----------------------------------------------------------------------
    // Filename generation (static — called by Mojo)
    // -----------------------------------------------------------------------

    /**
     * Build the output filename for a feature PDF.
     *
     * Pattern: {@code featureName@QTEST_TC_NNNN.pdf}
     * If no qTest tag: {@code featureName.pdf}
     */
    public static String generateFilename(CucumberFeature feature, String qtestCaseId) {
        String name = feature.getName() != null ? feature.getName() : "unnamed";
        // Slugify
        String slug = name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (slug.isEmpty()) slug = "feature";
        String caseTag = qtestCaseId != null && !qtestCaseId.isBlank()
                ? "@QTEST_TC_" + qtestCaseId : "";
        return slug + caseTag + ".pdf";
    }

    // -----------------------------------------------------------------------
    // Generation
    // -----------------------------------------------------------------------

    /**
     * Generate a PDF for a single feature and write it to {@code outputPath}.
     */
    public void generateFeaturePdf(CucumberFeature feature,
                                    String outputPath) throws IOException {
        File outFile = new File(outputPath);
        File parent  = outFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create output dir: " + parent);
        }

        try (PDDocument doc = new PDDocument()) {
            addFeaturePage(doc, feature);

            if (includeDetailedPages) {
                for (CucumberScenario sc : feature.getActualScenarios()) {
                    addScenarioPage(doc, feature, sc);
                }
            }

            doc.save(outFile);
        }
        log.debug("Written: {}", outputPath);
    }

    // -----------------------------------------------------------------------
    // Feature summary page
    // -----------------------------------------------------------------------

    private void addFeaturePage(PDDocument doc, CucumberFeature feature) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        float y = PH - M;

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            // Header band
            s.fillRect(cs, 0, y - 50f, PW, 50f, ColorScheme.HEADER);
            s.fillRect(cs, 0, y - 50f, PW, 3f, ColorScheme.forStatus(feature.getOverallStatus()));
            s.drawText(doc, cs, safe(feature.getName()),
                    M, y - 18f, s.boldFont(), 16f, ColorScheme.TEXT_WHITE);
            s.drawText(doc, cs, safe(feature.getUri()),
                    M, y - 36f, s.regularFont(), 8f, ColorScheme.TEXT_HINT);
            y -= 66f;

            // Stats row
            s.drawText(doc, cs,
                    "Scenarios: " + feature.getTotalScenarios()
                    + "   Passed: " + feature.getPassedScenarios()
                    + "   Failed: " + feature.getFailedScenarios()
                    + "   Skipped: " + feature.getSkippedScenarios(),
                    M, y, s.regularFont(), 10f, ColorScheme.TEXT_SECONDARY);
            y -= 24f;

            // Progress bar
            s.drawProgressBar(cs, M, y - 8f, CW, 8f,
                    feature.getPassedSteps(),
                    feature.getFailedSteps(),
                    feature.getSkippedSteps());
            y -= 24f;

            // Scenario list
            for (CucumberScenario sc : feature.getActualScenarios()) {
                if (y < M + 30) break;
                String st = sc.getStatus();
                s.dot(cs, M + 4f, y + 3f, 4f, ColorScheme.forStatus(st));
                s.drawText(doc, cs, safe(sc.getName()),
                        M + 16f, y, s.regularFont(), 9f, ColorScheme.TEXT_SECONDARY);
                s.drawText(doc, cs, st,
                        PW - M - 60f, y, s.boldFont(), 9f, ColorScheme.forStatus(st));
                y -= LH;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Scenario detail page
    // -----------------------------------------------------------------------

    private void addScenarioPage(PDDocument doc,
                                  CucumberFeature feature,
                                  CucumberScenario sc) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        float y = PH - M;

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            // Scenario header
            String st = sc.getStatus();
            s.fillRect(cs, 0, y - 36f, PW, 36f, ColorScheme.HEADER);
            s.fillRect(cs, 0, y - 36f, PW, 3f, ColorScheme.forStatus(st));
            s.drawText(doc, cs, trunc(safe(feature.getName()), 70),
                    M, y - 12f, s.regularFont(), 7f, ColorScheme.TEXT_HINT);
            s.drawText(doc, cs, trunc(safe(sc.getName()), 72),
                    M, y - 26f, s.boldFont(), 11f, ColorScheme.TEXT_WHITE);
            y -= 50f;

            // Steps
            for (CucumberStep step : sc.getSteps()) {
                if (y < M + 20) { y = addContinuationPage(doc, cs, sc); }
                String kw  = safe(step.getKeyword());
                String nm  = trunc(safe(step.getName()), 90);
                String sts = step.getStatus();
                s.dot(cs, M + 4f, y + 3f, 3f, ColorScheme.forStatus(sts));
                s.drawText(doc, cs, kw, M + 14f, y, s.boldFont(), 9f, ColorScheme.ACCENT);
                s.drawText(doc, cs, nm, M + 14f + kw.length() * 5.5f, y,
                        s.regularFont(), 9f, ColorScheme.TEXT_SECONDARY);
                s.drawText(doc, cs, step.getDurationMillis() + "ms",
                        PW - M - 50f, y, s.regularFont(), 7f, ColorScheme.TEXT_HINT);
                y -= LH;

                // Error block (truncated)
                String err = step.getErrorMessage();
                if (err != null && !err.isEmpty()) {
                    String[] lines = err.split("\\r?\\n");
                    int shown = Math.min(lines.length, 6);
                    float bH = shown * 11f + 8f;
                    if (y - bH < M) break;
                    s.fillRect(cs, M + 8f, y - bH, CW - 8f, bH, ColorScheme.FAILED_BG);
                    s.fillRect(cs, M + 8f, y - bH, 2f, bH, ColorScheme.FAILED);
                    float ty = y - 7f;
                    for (int i = 0; i < shown; i++) {
                        s.drawText(doc, cs, trunc(lines[i], 100),
                                M + 18f, ty, s.monoFont(), 7.5f, ColorScheme.FAILED_TEXT);
                        ty -= 11f;
                    }
                    y -= bH + 4f;
                }
            }
        }
    }

    /** Add a new page and return the starting Y. Used when a scenario overflows. */
    private float addContinuationPage(PDDocument doc,
                                       PDPageContentStream cs,
                                       CucumberScenario sc) {
        // In a real multi-page scenario we'd open a new stream, but
        // for this simplified generator we just clamp Y and accept truncation.
        return M + 20f;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static String safe(String v)         { return v != null ? v : ""; }
    private static String trunc(String v, int n) {
        if (v == null) return "";
        return v.length() > n ? v.substring(0, n - 3) + "..." : v;
    }
}
