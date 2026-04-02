package com.nosuchelements.pdf;

import com.nosuchelements.cucumber.model.CucumberFeature;
import com.nosuchelements.cucumber.model.CucumberScenario;
import com.nosuchelements.cucumber.model.CucumberStep;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

/**
 * Generates a single per-feature PDF (split mode).
 *
 * <p>This version fixes two issues from the original implementation:</p>
 * <ul>
 *   <li>Proper multi-page support for long scenarios (stack traces, logs)</li>
 *   <li>Screenshot rendering for steps and hooks via {@link CucumberStep#getEmbeddings()}</li>
 * </ul>
 */
public class FeaturePdfGenerator {

    private static final Logger log = LoggerFactory.getLogger(FeaturePdfGenerator.class);

    private static final float M      = 36f;
    private static final float PAGE_W = PDRectangle.A4.getWidth();
    private static final float PAGE_H = PDRectangle.A4.getHeight();
    private static final float CONTENT_W = PAGE_W - 2 * M;
    private static final float BOT    = 44f;   // bottom dead-zone
    private static final float LH     = 14f;

    // Max screenshot dimensions (points)
    private static final float IMG_W = 490f;
    private static final float IMG_H = 310f;

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
        this.includeSummaryPage   = includeSummaryPage;
        this.includeFeaturePage   = includeFeaturePage;
        this.includeDetailedPages = includeDetailedPages;
        this.maxOutputLines       = Math.max(1, maxOutputLines);
        this.tagPrefix            = tagPrefix != null ? tagPrefix : "QTEST_TC_";
    }

    // -----------------------------------------------------------------------
    // Filename generation (static — called by Mojo)
    // -----------------------------------------------------------------------

    /**
     * Build the output filename for a feature PDF.
     * Pattern: feature-name@QTEST_TC_NNNN.pdf or feature-name.pdf.
     */
    public static String generateFilename(CucumberFeature feature, String qtestCaseId) {
        String name = feature.getName() != null ? feature.getName() : "unnamed";
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

    /** Generate a PDF for a single feature and write it to {@code outputPath}. */
    public void generateFeaturePdf(CucumberFeature feature,
                                   String outputPath) throws IOException {
        File outFile = new File(outputPath);
        File parent  = outFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create output dir: " + parent);
        }

        try (PDDocument doc = new PDDocument()) {
            if (includeFeaturePage) {
                addFeaturePage(doc, feature);
            }
            if (includeDetailedPages) {
                for (CucumberScenario sc : feature.getActualScenarios()) {
                    addScenarioPages(doc, feature, sc);
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
        float y = PAGE_H - M;

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            // Header band
            s.fillRect(cs, 0, y - 50f, PAGE_W, 50f, ColorScheme.HEADER);
            s.fillRect(cs, 0, y - 50f, PAGE_W, 3f, ColorScheme.forStatus(feature.getOverallStatus()));
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
            s.drawProgressBar(cs, M, y - 8f, CONTENT_W, 8f,
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
                        PAGE_W - M - 60f, y, s.boldFont(), 9f, ColorScheme.forStatus(st));
                y -= LH;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Scenario detail pages (multi-page aware)
    // -----------------------------------------------------------------------

    private void addScenarioPages(PDDocument doc,
                                  CucumberFeature feature,
                                  CucumberScenario sc) throws IOException {
        ScenarioCursor cur = new ScenarioCursor(doc, feature, sc);
        cur.startScenarioHeader();

        // Background steps first
        for (CucumberStep bg : sc.getBackgroundSteps()) {
            cur.renderStep(bg, true);
        }

        // Before hooks
        for (CucumberStep hook : sc.getBeforeHooks()) {
            cur.renderHookEntry(hook, "Before hook");
        }

        // Main steps
        for (CucumberStep step : sc.getSteps()) {
            cur.renderStep(step, false);
        }

        // After hooks
        for (CucumberStep hook : sc.getAfterHooks()) {
            cur.renderHookEntry(hook, "After hook");
        }
    }

    // Cursor responsible for multi-page scenario rendering
    private class ScenarioCursor {
        final PDDocument      doc;
        final CucumberFeature feature;
        final CucumberScenario sc;

        PDPage page;
        float  y;

        ScenarioCursor(PDDocument doc, CucumberFeature feature, CucumberScenario sc) {
            this.doc     = doc;
            this.feature = feature;
            this.sc      = sc;
        }

        void newPage(boolean continued) throws IOException {
            page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            y = PAGE_H - M;
            if (continued) {
                try (PDPageContentStream cs = new PDPageContentStream(doc, page,
                        PDPageContentStream.AppendMode.APPEND, true)) {
                    s.fillRect(cs, 0, y - 18f, PAGE_W, 18f, ColorScheme.ROW_ALT);
                    s.drawText(doc, cs,
                            safe(sc.getName()) + "  (continued)",
                            M, y - 13f, s.regularFont(), 8f, ColorScheme.TEXT_HINT);
                }
                y -= 24f;
            }
        }

        void ensureSpace(float needed) throws IOException {
            if (page == null || y - needed < BOT) {
                newPage(page != null);
            }
        }

        PDPageContentStream openStream() throws IOException {
            return new PDPageContentStream(doc, page,
                    PDPageContentStream.AppendMode.APPEND, true);
        }

        void startScenarioHeader() throws IOException {
            ensureSpace(60f);
            try (PDPageContentStream cs = openStream()) {
                String st = sc.getStatus();
                s.fillRect(cs, 0, y - 36f, PAGE_W, 36f, ColorScheme.HEADER);
                s.fillRect(cs, 0, y - 36f, PAGE_W, 3f, ColorScheme.forStatus(st));
                s.drawText(doc, cs, trunc(safe(feature.getName()), 70),
                        M, y - 12f, s.regularFont(), 7f, ColorScheme.TEXT_HINT);
                s.drawText(doc, cs, trunc(safe(sc.getName()), 72),
                        M, y - 26f, s.boldFont(), 11f, ColorScheme.TEXT_WHITE);
            }
            y -= 50f;
        }

        void renderStep(CucumberStep step, boolean isBackground) throws IOException {
            ensureSpace(LH + 6f);
            String kw  = safe(step.getKeyword());
            String nm  = trunc(safe(step.getName()), 90);
            String sts = step.getStatus();

            try (PDPageContentStream cs = openStream()) {
                s.dot(cs, M + 4f, y + 3f, 3f, ColorScheme.forStatus(sts));
                if (!kw.isEmpty()) {
                    s.drawText(doc, cs, kw, M + 14f, y,
                            s.boldFont(), 9f,
                            isBackground ? ColorScheme.TEXT_MUTED : ColorScheme.ACCENT);
                }
                float nameX = M + 14f + (kw.isEmpty() ? 0 : kw.length() * 5.5f);
                s.drawText(doc, cs, nm, nameX, y,
                        s.regularFont(), 9f,
                        isBackground ? ColorScheme.TEXT_MUTED : ColorScheme.TEXT_SECONDARY);
                s.drawText(doc, cs, step.getDurationMillis() + "ms",
                        PAGE_W - M - 50f, y,
                        s.regularFont(), 7f, ColorScheme.TEXT_HINT);
            }
            y -= LH;

            // Error block
            String err = step.getErrorMessage();
            if (err != null && !err.isEmpty()) {
                renderErrorBlock(err, 8);
            }

            // Embeddings (screenshots)
            renderEmbeddings(step.getEmbeddings());
        }

        void renderHookEntry(CucumberStep hook, String label) throws IOException {
            ensureSpace(LH + 6f);
            String sts = hook.getStatus();
            try (PDPageContentStream cs = openStream()) {
                s.dot(cs, M + 4f, y + 3f, 3f, ColorScheme.forStatus(sts));
                s.drawText(doc, cs, label,
                        M + 14f, y, s.boldFont(), 9f, ColorScheme.TEXT_MUTED);
                s.drawText(doc, cs, hook.getDurationMillis() + "ms",
                        PAGE_W - M - 50f, y,
                        s.regularFont(), 7f, ColorScheme.TEXT_HINT);
            }
            y -= LH;

            if (hook.getErrorMessage() != null && !hook.getErrorMessage().isEmpty()) {
                renderErrorBlock(hook.getErrorMessage(), 8);
            }
            renderEmbeddings(hook.getEmbeddings());
        }

        void renderErrorBlock(String error, int maxLines) throws IOException {
            String[] lines = error.split("\\r?\\n");
            int shown = Math.min(lines.length, maxLines);
            float bH  = shown * 11f + (lines.length > maxLines ? 11f : 0) + 12f;
            ensureSpace(bH + 4f);

            float bX = M + 8f, bW = CONTENT_W - 8f, bY = y - bH;
            try (PDPageContentStream cs = openStream()) {
                s.fillRect(cs, bX, bY, bW, bH, ColorScheme.FAILED_BG);
                s.fillRect(cs, bX, bY, 2f, bH, ColorScheme.FAILED);
                float ty = y - 7f;
                for (int i = 0; i < shown; i++) {
                    String ln = lines[i] != null ? lines[i].replace("\t", "    ") : "";
                    s.drawText(doc, cs, trunc(ln, 110),
                            bX + 10f, ty, s.monoFont(), 7.5f, ColorScheme.FAILED_TEXT);
                    ty -= 11f;
                }
                if (lines.length > maxLines) {
                    s.drawText(doc, cs,
                            "... +" + (lines.length - maxLines) + " more lines",
                            bX + 10f, ty, s.monoFont(), 7.5f, ColorScheme.TEXT_HINT);
                }
            }
            y -= (bH + 4f);
        }

        void renderEmbeddings(List<String> shots) throws IOException {
            if (shots == null || shots.isEmpty()) return;
            for (String b64 : shots) {
                if (b64 == null || b64.isBlank()) continue;
                try {
                    byte[] bytes = Base64.getDecoder().decode(b64.trim());
                    PDImageXObject img = PDImageXObject.createFromByteArray(doc, bytes, "screenshot");
                    float scale = Math.min(Math.min(IMG_W / img.getWidth(), IMG_H / img.getHeight()), 1f);
                    float dW = img.getWidth() * scale;
                    float dH = img.getHeight() * scale;
                    ensureSpace(dH + 20f);
                    float fX = M + 4f, fW = CONTENT_W - 4f, fY = y - dH - 8f;
                    try (PDPageContentStream cs = openStream()) {
                        s.drawCard(cs, fX, fY - 4f, fW, dH + 8f);
                        cs.drawImage(img, fX + (fW - dW) / 2f, fY, dW, dH);
                    }
                    y -= (dH + 14f);
                } catch (Exception e) {
                    log.warn("Screenshot decode failed ({}): {}", b64.length(), e.getMessage());
                    ensureSpace(18f);
                    try (PDPageContentStream cs = openStream()) {
                        s.drawText(doc, cs,
                                "[Screenshot decode failed: " + e.getMessage() + "]",
                                M + 10f, y, s.monoFont(), 8f, ColorScheme.FAILED_TEXT);
                    }
                    y -= 18f;
                }
            }
        }
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
