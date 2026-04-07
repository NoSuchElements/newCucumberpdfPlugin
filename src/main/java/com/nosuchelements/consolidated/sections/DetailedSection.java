package com.nosuchelements.consolidated.sections;

import com.nosuchelements.consolidated.ContentBlockRenderer;
import com.nosuchelements.consolidated.ConsolidatedPageCursor;
import com.nosuchelements.consolidated.PluginVersion;
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
 * Detailed section — step-by-step breakdown for every scenario.
 *
 * <h3>Fixes applied</h3>
 * <ul>
 *   <li><b>D5</b> – Before-hook output logs are now rendered immediately after
 *       before-hook errors, matching the treatment of after-hooks.</li>
 *   <li><b>D6</b> – Duration label x-position computed from {@code dur.length()}
 *       with a per-char width, preventing collision with the status badge on
 *       long duration strings (e.g. "10m 55s").</li>
 *   <li><b>D7</b> – Background step errors and embedded screenshots are now
 *       rendered below each background step line, making root-cause failures
 *       in {@code @Before} / background setup steps immediately visible.</li>
 *   <li><b>CC1</b> – {@code renderErrorBlock} called with {@code -1} so
 *       {@link ContentBlockRenderer} uses the configured {@code maxOutputLines}
 *       rather than a hardcoded magic number.</li>
 *   <li><b>D4</b> – Footer uses {@link PluginVersion#FULL}.</li>
 *   <li><b>SP-5</b> – Post-step trailing gap increased from {@code 4f} to
 *       {@code 8f} so there is a clear visual separation between the last
 *       content block of one step and the dot/keyword of the next step.</li>
 * </ul>
 */
public class DetailedSection {

    private static final float M    = ConsolidatedPageCursor.MARGIN_H;
    private static final float CW   = ConsolidatedPageCursor.CONTENT_W;
    private static final float SHDR = 38f;
    private static final float LMD  = 15f;

    /** Approximate character width for the footer font (7f). */
    private static final float DUR_CHAR_W = 5.0f;

    /**
     * SP-5: trailing gap after the last content block of a step.
     * Increased from 4f → 8f so the next step's bullet clearly belongs to
     * the next row rather than the previous one's attachments.
     */
    private static final float STEP_TRAIL_GAP = 8f;

    private final PdfStyler            styler;
    private final ContentBlockRenderer renderer;

    public DetailedSection(PdfStyler styler, int maxOutputLines) {
        this.styler   = styler;
        this.renderer = new ContentBlockRenderer(styler, maxOutputLines);
    }

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

        cur.ensureSpace(SHDR + LMD * 3);
        drawScenarioHeader(cur, feature, sc);
        cur.advance(6f);

        // D5 fix: before-hook errors + logs + screenshots
        renderHookBlock(cur, sc.getBeforeHooks(), "Before hook failed");

        // Background steps
        if (sc.hasBackground()) {
            renderBackgroundBlock(cur, sc.getBackgroundSteps());
        }

        // Steps
        for (CucumberStep step : sc.getSteps()) {
            renderStep(cur, step);
        }

        // After-hook errors + logs + screenshots
        renderHookBlock(cur, sc.getAfterHooks(), "After hook failed");
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

        try (PDPageContentStream cs = cs(cur)) {
            styler.fillRect(cs, M, cur.y - SHDR, CW, SHDR, ColorScheme.HEADER);
            styler.fillRect(cs, M, cur.y - SHDR, CW, 3f, ColorScheme.forStatus(st));
            styler.drawText(cur.doc, cs, featName,
                    M + 8f, cur.y - 11f,
                    styler.regularFont(), 7f, ColorScheme.TEXT_HINT);
            styler.drawText(cur.doc, cs, scenName,
                    M + 8f, cur.y - 26f,
                    styler.boldFont(), 11f, ColorScheme.TEXT_WHITE);

            float bW = 66f, bH = 17f;
            float bX = M + CW - bW, bMid = cur.y - SHDR + SHDR / 2f;
            styler.fillRect(cs, bX, bMid - bH / 2f, bW, bH, ColorScheme.forStatus(st));
            styler.drawText(cur.doc, cs, st,
                    bX + 6f, bMid - 4f, styler.boldFont(), 8.5f, ColorScheme.TEXT_WHITE);

            // D6 fix: compute x from actual string width
            String dur = sc.formatDuration();
            float durW = dur.length() * DUR_CHAR_W;
            styler.drawText(cur.doc, cs, dur,
                    bX - durW - 8f, bMid - 4f,
                    styler.regularFont(), 8f, ColorScheme.TEXT_HINT);
        }
        cur.advance(SHDR + 5f);
    }

    // -----------------------------------------------------------------------
    // Background block
    // -----------------------------------------------------------------------

    /**
     * Render background steps with a labelled header and a separator line.
     *
     * <p>D7 fix: after each background step line, any error message and
     * embedded screenshots are rendered (a failing background step was
     * previously invisible).</p>
     */
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
                styler.fillRect(cs, M + 3f, cur.y + 1f, 5f, 5f, ColorScheme.TEXT_HINT);
                styler.drawText(cur.doc, cs, kw, M + 14f, cur.y,
                        styler.boldFont(), 8.5f, ColorScheme.TEXT_HINT);
                float nx = M + 14f + kw.length() * 5f;
                styler.drawText(cur.doc, cs, trunc(nm, 90), nx, cur.y,
                        styler.regularFont(), 8.5f, ColorScheme.TEXT_HINT);
            }
            cur.advance(LMD);

            // D7 fix: render background step errors
            String err = step.getErrorMessage();
            if (err != null && !err.isEmpty()) {
                try (PDPageContentStream cs = cs(cur)) {
                    styler.drawText(cur.doc, cs, "Background step failed",
                            M, cur.y, styler.boldFont(), 8f, ColorScheme.FAILED_TEXT);
                }
                cur.advance(LMD);
                renderer.renderErrorBlock(cur, err, -1);
            }

            // D7 fix: render background step screenshots
            if (!step.getEmbeddings().isEmpty()) {
                renderer.renderScreenshotGroup(cur, step.getEmbeddings(), err != null && !err.isEmpty());
            }
        }

        cur.ensureSpace(10f);
        try (PDPageContentStream cs = cs(cur)) {
            styler.hLine(cs, M, M + CW, cur.y, ColorScheme.BORDER, 0.5f);
        }
        cur.advance(9f);
    }

    // -----------------------------------------------------------------------
    // Hook block (errors + output logs + screenshots)
    // -----------------------------------------------------------------------

    /**
     * Render hook errors, output logs, and screenshots.
     *
     * <p>D5 fix: this unified method handles both before- and after-hooks;
     * previously before-hook output logs were missing from this path.</p>
     */
    private void renderHookBlock(ConsolidatedPageCursor cur,
                                  List<CucumberStep> hooks,
                                  String label) throws IOException {
        for (CucumberStep hook : hooks) {
            String err    = hook.getErrorMessage();
            boolean hasErr  = err != null && !err.isEmpty();
            boolean hasShot = !hook.getEmbeddings().isEmpty();
            boolean hasLog  = !hook.getOutputLines().isEmpty();

            if (!hasErr && !hasShot && !hasLog) continue;

            if (hasErr) {
                cur.ensureSpace(LMD + 36f);
                try (PDPageContentStream cs = cs(cur)) {
                    styler.drawText(cur.doc, cs, label, M, cur.y,
                            styler.boldFont(), 8.5f, ColorScheme.FAILED_TEXT);
                }
                cur.advance(LMD);
                renderer.renderErrorBlock(cur, err, -1);
            }

            // D5 fix: render hook output logs (before- and after-hooks)
            if (hasLog) {
                renderer.renderLogs(cur, hook.getOutputLines(), "Hook output");
            }

            if (hasShot) {
                renderer.renderScreenshotGroup(cur, hook.getEmbeddings(), hasErr);
            }
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

        int      avail = ContentBlockRenderer.availStepChars(kw.length(), cont);
        String[] nml   = ContentBlockRenderer.wrapStepName(nm, avail);
        float    stepH = nml.length > 1 ? LMD * 2f + 4f : LMD + 4f;
        cur.ensureSpace(stepH);

        try (PDPageContentStream cs = cs(cur)) {
            float y = cur.y;
            float W = ConsolidatedPageCursor.PAGE_W;

            if (cont) {
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

            String ds = dur + "ms";
            styler.drawText(cur.doc, cs, ds, W - M - ds.length() * 5f, y,
                    styler.regularFont(), 7.5f, ColorScheme.TEXT_HINT);

            styler.hLine(cs, M, W - M, y - 5f, ColorScheme.BORDER_SUBTLE, 0.3f);
        }

        cur.advance(nml.length > 1 ? LMD * 2f : LMD);

        // Per-step content blocks — each renderer method adds its own BLOCK_GAP_BEFORE
        String err = step.getErrorMessage();
        if (err != null && !err.isEmpty()) {
            renderer.renderErrorBlock(cur, err, -1);
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
        if (!step.getEmbeddings().isEmpty()) {
            renderer.renderScreenshotGroup(cur, step.getEmbeddings(), true);
        }

        // SP-5: increased from 4f → 8f — clear gap before next step's bullet
        cur.advance(STEP_TRAIL_GAP);
    }

    // -----------------------------------------------------------------------
    // Section footer
    // -----------------------------------------------------------------------

    private void drawSectionFooter(ConsolidatedPageCursor cur) throws IOException {
        try (PDPageContentStream cs = new PDPageContentStream(
                cur.doc, cur.page, PDPageContentStream.AppendMode.APPEND, true)) {
            float W = ConsolidatedPageCursor.PAGE_W;
            styler.hLine(cs, M, W - M, 28f, ColorScheme.BORDER, 0.4f);
            styler.drawText(cur.doc, cs,
                    PluginVersion.FULL + "  |  Detailed Steps",
                    M, 14f, styler.regularFont(), 7f, ColorScheme.TEXT_HINT);
        }
    }

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
