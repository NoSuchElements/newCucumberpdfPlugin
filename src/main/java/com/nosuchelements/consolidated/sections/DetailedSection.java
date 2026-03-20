package com.nosuchelements.consolidated.sections;

import com.nosuchelements.consolidated.ContentBlockRenderer;
import com.nosuchelements.consolidated.ConsolidatedPageCursor;
import com.nosuchelements.consolidated.SectionHeader;
import com.nosuchelements.consolidated.TableOfContents;
import com.nosuchelements.cucumber.model.CucumberFeature;
import com.nosuchelements.cucumber.model.CucumberScenario;
import com.nosuchelements.cucumber.model.CucumberStep;
import com.nosuchelements.pdf.ColorScheme;
import com.nosuchelements.pdf.PdfStyler;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import java.io.IOException;
import java.util.List;

/**
 * Detailed section — step-by-step breakdown for every scenario across all features.
 *
 * <h3>Page structure per scenario</h3>
 * <pre>
 * ┌──────────────────────────────────────────────────────────┐
 * │  Feature Name  (breadcrumb)                              │  ← slate-900 band
 * │  Scenario Name                            [PASSED] 2.3s │
 * └──────────────────────────────────────────────────────────┘
 *   [Background steps, if any]
 *   [Before-hook errors]
 *   ●  Given  I navigate to the homepage                10ms
 *   ●  When   I click the login button                  22ms
 *   ●  Then   I should see the dashboard                18ms
 *       And   the menu is visible                        5ms    ← italic, indented (F-01)
 *   [Error block if failed]
 *   [Step output logs]
 *   [Data table if present]
 *   [DocString if present]
 *   [After-hook errors]
 * </pre>
 *
 * <p>Delegates all block rendering to {@link ContentBlockRenderer}.</p>
 */
public class DetailedSection {

    private static final float M    = ConsolidatedPageCursor.MARGIN_H;
    private static final float CW   = ConsolidatedPageCursor.CONTENT_W;
    private static final float SHDR = 38f;   // scenario header band height
    private static final float LSM  = 11f;
    private static final float LMD  = 15f;

    private final PdfStyler            styler;
    private final ContentBlockRenderer renderer;

    public DetailedSection(PdfStyler styler, int maxOutputLines) {
        this.styler   = styler;
        this.renderer = new ContentBlockRenderer(styler, maxOutputLines);
    }

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public void build(PDDocument doc, PDPage firstPage,
                      List<CucumberFeature> features,
                      TableOfContents toc) throws IOException {

        ConsolidatedPageCursor cur = new ConsolidatedPageCursor(
                doc, firstPage, styler, "Detailed Steps");

        toc.add("Detailed", cur.currentPageIndex());

        SectionHeader.draw(cur, styler,
                "Detailed Steps", null, ColorScheme.FAILED);

        for (CucumberFeature feature : features) {
            for (CucumberScenario scenario : feature.getActualScenarios()) {
                renderScenario(cur, feature, scenario);
                cur.advance(14f);
            }
        }

        drawSectionFooter(cur);
    }

    // -----------------------------------------------------------------------
    // Scenario block
    // -----------------------------------------------------------------------

    private void renderScenario(ConsolidatedPageCursor cur,
                                 CucumberFeature feature,
                                 CucumberScenario sc) throws IOException {

        // Require at least header + 3 steps before allowing a page break
        cur.ensureSpace(SHDR + LMD * 3);

        drawScenarioHeader(cur, feature, sc);

        // Before-hook errors
        renderHookErrors(cur, sc.getBeforeHooks(), "Before hook failed");

        // Background steps
        if (sc.hasBackground()) {
            renderBackgroundBlock(cur, sc.getBackgroundSteps());
        }

        // Steps
        for (CucumberStep step : sc.getSteps()) {
            renderStep(cur, step);
        }

        // After-hook errors
        renderHookErrors(cur, sc.getAfterHooks(), "After hook failed");

        // After-hook output logs
        for (CucumberStep hook : sc.getAfterHooks()) {
            List<String> out = hook.getOutputLines();
            if (!out.isEmpty()) renderer.renderLogs(cur, out, "Hook output");
        }
    }

    // -----------------------------------------------------------------------
    // Scenario header band
    // -----------------------------------------------------------------------

    private void drawScenarioHeader(ConsolidatedPageCursor cur,
                                     CucumberFeature feature,
                                     CucumberScenario sc) throws IOException {
        String st       = sc.getStatus();
        String featName = trunc(safe(feature.getName()), 65);
        String scenName = trunc(safe(sc.getName()), 68);
        float  W        = ConsolidatedPageCursor.PAGE_W;

        try (PDPageContentStream cs = cs(cur)) {
            // Band background
            styler.fillRect(cs, M, cur.y - SHDR, CW, SHDR, ColorScheme.HEADER);
            // Bottom accent stripe
            styler.fillRect(cs, M, cur.y - SHDR, CW, 3f, ColorScheme.forStatus(st));

            // Feature breadcrumb  (top line, small, muted)
            styler.drawText(cur.doc, cs, featName,
                    M + 8f, cur.y - 11f,
                    styler.regularFont(), 7f, ColorScheme.TEXT_HINT);

            // Scenario name  (main line, bold, white)
            styler.drawText(cur.doc, cs, scenName,
                    M + 8f, cur.y - 26f,
                    styler.boldFont(), 11f, ColorScheme.TEXT_WHITE);

            // Status badge  (right side, vertically centred)
            float bW = 66f, bH = 17f;
            float bX = M + CW - bW, bMid = cur.y - SHDR + SHDR / 2f;
            styler.fillRect(cs, bX, bMid - bH / 2f, bW, bH, ColorScheme.forStatus(st));
            styler.drawText(cur.doc, cs, st,
                    bX + 6f, bMid - 4f, styler.boldFont(), 8.5f, ColorScheme.TEXT_WHITE);

            // Duration (just left of badge)
            String dur = sc.formatDuration();
            styler.drawText(cur.doc, cs, dur,
                    bX - dur.length() * 5f - 8f, bMid - 4f,
                    styler.regularFont(), 8f, ColorScheme.TEXT_HINT);
        }
        cur.advance(SHDR + 5f);
    }

    // -----------------------------------------------------------------------
    // Background block
    // -----------------------------------------------------------------------

    private void renderBackgroundBlock(ConsolidatedPageCursor cur,
                                        List<CucumberStep> steps) throws IOException {
        cur.ensureSpace(LMD + steps.size() * LMD + 10f);

        try (PDPageContentStream cs = cs(cur)) {
            styler.drawText(cur.doc, cs, "Background",
                    M, cur.y, styler.boldFont(), 8f, ColorScheme.TEXT_HINT);
        }
        cur.advance(LMD);

        for (CucumberStep step : steps) {
            cur.ensureSpace(LMD);
            String kw = safe(step.getKeyword());
            String nm = safe(step.getName());
            try (PDPageContentStream cs = cs(cur)) {
                // Small filled square bullet
                styler.fillRect(cs, M + 3f, cur.y + 1f, 5f, 5f, ColorScheme.TEXT_HINT);
                styler.drawText(cur.doc, cs, kw, M + 14f, cur.y,
                        styler.boldFont(), 8.5f, ColorScheme.TEXT_HINT);
                float nx = M + 14f + kw.length() * 5f;
                styler.drawText(cur.doc, cs, trunc(nm, 90), nx, cur.y,
                        styler.regularFont(), 8.5f, ColorScheme.TEXT_HINT);
            }
            cur.advance(LMD);
        }

        cur.ensureSpace(10f);
        try (PDPageContentStream cs = cs(cur)) {
            styler.hLine(cs, M, M + CW, cur.y, ColorScheme.BORDER, 0.5f);
        }
        cur.advance(9f);
    }

    // -----------------------------------------------------------------------
    // Hook errors
    // -----------------------------------------------------------------------

    private void renderHookErrors(ConsolidatedPageCursor cur,
                                   List<CucumberStep> hooks,
                                   String label) throws IOException {
        for (CucumberStep hook : hooks) {
            String err = hook.getErrorMessage();
            if (err == null || err.isEmpty()) continue;
            cur.ensureSpace(LMD + 36f);
            try (PDPageContentStream cs = cs(cur)) {
                styler.drawText(cur.doc, cs, label, M, cur.y,
                        styler.boldFont(), 8.5f, ColorScheme.FAILED_TEXT);
            }
            cur.advance(LMD);
            renderer.renderErrorBlock(cur, err, 8);
        }
    }

    // -----------------------------------------------------------------------
    // Individual step
    // -----------------------------------------------------------------------

    private void renderStep(ConsolidatedPageCursor cur,
                             CucumberStep step) throws IOException {

        String kw   = safe(step.getKeyword());
        String nm   = safe(step.getName());
        String st   = step.getStatus();
        long   dur  = step.getDurationMillis();
        boolean cont = ContentBlockRenderer.isContinuationKeyword(kw);

        // Pre-compute wrap to know height before drawing
        int      avail = ContentBlockRenderer.availStepChars(kw.length(), cont);
        String[] nml   = ContentBlockRenderer.wrapStepName(nm, avail);
        float    stepH = nml.length > 1 ? LMD * 2f + 4f : LMD + 4f;
        cur.ensureSpace(stepH);

        try (PDPageContentStream cs = cs(cur)) {
            float y = cur.y;
            float W = ConsolidatedPageCursor.PAGE_W;

            if (cont) {
                // F-01 continuation: italic, extra indent, smaller dot
                styler.dot(cs, M + 13f, y + 3f, 2.5f, ColorScheme.TEXT_HINT);
                styler.drawText(cur.doc, cs, kw, M + 22f, y,
                        styler.italicFont(), 9.5f, ColorScheme.TEXT_SECONDARY);
                float nx = M + 22f + kw.length() * 5.1f;
                styler.drawText(cur.doc, cs, nml[0], nx, y,
                        styler.regularFont(), 9.5f, ColorScheme.TEXT_SECONDARY);
                if (nml.length > 1 && !nml[1].isEmpty()) {
                    styler.drawText(cur.doc, cs, nml[1], nx, y - LMD,
                            styler.regularFont(), 9.5f, ColorScheme.TEXT_SECONDARY);
                }
            } else {
                // Primary clause: coloured dot, bold keyword in accent colour
                styler.dot(cs, M + 5f, y + 3f, 3.5f, ColorScheme.forStatus(st));
                styler.drawText(cur.doc, cs, kw, M + 14f, y,
                        styler.boldFont(), 9.5f, ColorScheme.ACCENT);
                float nx = M + 14f + kw.length() * 5.5f;
                styler.drawText(cur.doc, cs, nml[0], nx, y,
                        styler.regularFont(), 9.5f, ColorScheme.TEXT_SECONDARY);
                if (nml.length > 1 && !nml[1].isEmpty()) {
                    styler.drawText(cur.doc, cs, nml[1], nx, y - LMD,
                            styler.regularFont(), 9.5f, ColorScheme.TEXT_SECONDARY);
                }
            }

            // Duration — right-aligned
            String ds = dur + "ms";
            styler.drawText(cur.doc, cs, ds, W - M - ds.length() * 5f, y,
                    styler.regularFont(), 7.5f, ColorScheme.TEXT_HINT);

            // Row separator
            styler.hLine(cs, M, W - M, y - 5f, ColorScheme.BORDER_SUBTLE, 0.3f);
        }

        cur.advance(nml.length > 1 ? LMD * 2f : LMD);

        // Per-step content blocks
        String err = step.getErrorMessage();
        if (err != null && !err.isEmpty()) {
            renderer.renderErrorBlock(cur, err, 8);
        }
        if (!step.getOutputLines().isEmpty()) {
            renderer.renderLogs(cur, step.getOutputLines(), null);
        }
        if (!step.getDataTableRows().isEmpty()) {
            renderer.renderDataTable(cur, step.getDataTableRows());
        }
        if (step.getDocString() != null && step.getDocString().getContent() != null) {
            renderer.renderDocString(cur, step.getDocString().getContent());
        }

        cur.advance(4f);
    }

    // -----------------------------------------------------------------------
    // Section footer (page count + section label)
    // -----------------------------------------------------------------------

    private void drawSectionFooter(ConsolidatedPageCursor cur) throws IOException {
        try (PDPageContentStream cs = new PDPageContentStream(
                cur.doc, cur.page, PDPageContentStream.AppendMode.APPEND, true)) {
            float W = ConsolidatedPageCursor.PAGE_W;
            styler.hLine(cs, M, W - M, 28f, ColorScheme.BORDER, 0.4f);
            styler.drawText(cur.doc, cs,
                    "Cucumber PDF Reporter v1.2.0  |  Detailed Steps",
                    M, 14f, styler.regularFont(), 7f, ColorScheme.TEXT_HINT);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private PDPageContentStream cs(ConsolidatedPageCursor cur) throws IOException {
        return new PDPageContentStream(cur.doc, cur.page,
                PDPageContentStream.AppendMode.APPEND, true);
    }
    private static String safe(String v)         { return v != null ? v : ""; }
    private static String trunc(String v, int n) {
        if (v == null) return "";
        return v.length() > n ? v.substring(0, n - 3) + "..." : v;
    }
}
