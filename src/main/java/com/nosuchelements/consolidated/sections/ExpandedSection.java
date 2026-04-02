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
 * Expanded section — full step-by-step breakdown identical to Detailed but
 * with per-scenario background sections shown inline.
 *
 * <h3>Fixes applied</h3>
 * <ul>
 *   <li><b>E1</b> – Screenshots are now shown for <em>all</em> steps
 *       (not only failed ones), which is the intended behaviour in expanded mode
 *       since operators want to see the browser state at each step.</li>
 *   <li><b>E2</b> – DataTable and DocString attachments are now rendered below
 *       each step, matching the output of the Detailed section.</li>
 *   <li><b>CC1/CC2</b> – Shared {@link ContentBlockRenderer} used; all
 *       {@code renderErrorBlock} calls pass {@code -1} to respect configured
 *       {@code maxOutputLines} rather than a hardcoded 20.</li>
 *   <li><b>D4</b> – Footer uses {@link PluginVersion#FULL}.</li>
 * </ul>
 */
public class ExpandedSection {

    private static final float M    = ConsolidatedPageCursor.MARGIN_H;
    private static final float CW   = ConsolidatedPageCursor.CONTENT_W;
    private static final float SHDR = 38f;
    private static final float LMD  = 15f;
    private static final float DUR_CHAR_W = 5.0f;

    private final PdfStyler            styler;
    private final ContentBlockRenderer renderer;

    public ExpandedSection(PdfStyler styler, int maxOutputLines) {
        this.styler   = styler;
        this.renderer = new ContentBlockRenderer(styler, maxOutputLines);
    }

    // -----------------------------------------------------------------------

    public void build(PDDocument doc, PDPage firstPage,
                      List<CucumberFeature> features,
                      TableOfContents toc) throws IOException {

        ConsolidatedPageCursor cur = new ConsolidatedPageCursor(
                doc, firstPage, styler, "Expanded Steps");

        toc.add("Expanded", cur.currentPageIndex());

        SectionHeader.draw(cur, styler,
                "Expanded Steps", null, ColorScheme.ACCENT);

        for (CucumberFeature feature : features) {
            for (CucumberScenario scenario : feature.getActualScenarios()) {
                renderScenario(cur, feature, scenario);
                cur.advance(14f);
            }
        }

        drawSectionFooter(cur);
    }

    // -----------------------------------------------------------------------

    private void renderScenario(ConsolidatedPageCursor cur,
                                 CucumberFeature feature,
                                 CucumberScenario sc) throws IOException {
        cur.ensureSpace(SHDR + LMD * 3);
        drawScenarioHeader(cur, feature, sc);
        cur.advance(6f);

        renderHookBlock(cur, sc.getBeforeHooks(), "Before hook failed");

        if (sc.hasBackground()) {
            renderBackgroundBlock(cur, sc.getBackgroundSteps());
        }

        for (CucumberStep step : sc.getSteps()) {
            renderStep(cur, step);
        }

        renderHookBlock(cur, sc.getAfterHooks(), "After hook failed");
    }

    // -----------------------------------------------------------------------

    private void drawScenarioHeader(ConsolidatedPageCursor cur,
                                     CucumberFeature feature,
                                     CucumberScenario sc) throws IOException {
        String st       = sc.getStatus();
        String featName = trunc(safe(feature.getName()), 65);
        String scenName = trunc(safe(sc.getName()), 68);
        float  W        = ConsolidatedPageCursor.PAGE_W;

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

            String dur = sc.formatDuration();
            float durW = dur.length() * DUR_CHAR_W;
            styler.drawText(cur.doc, cs, dur,
                    bX - durW - 8f, bMid - 4f,
                    styler.regularFont(), 8f, ColorScheme.TEXT_HINT);
        }
        cur.advance(SHDR + 5f);
    }

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
                styler.fillRect(cs, M + 3f, cur.y + 1f, 5f, 5f, ColorScheme.TEXT_HINT);
                styler.drawText(cur.doc, cs, kw, M + 14f, cur.y,
                        styler.boldFont(), 8.5f, ColorScheme.TEXT_HINT);
                float nx = M + 14f + kw.length() * 5f;
                styler.drawText(cur.doc, cs, trunc(nm, 90), nx, cur.y,
                        styler.regularFont(), 8.5f, ColorScheme.TEXT_HINT);
            }
            cur.advance(LMD);

            String err = step.getErrorMessage();
            if (err != null && !err.isEmpty()) {
                try (PDPageContentStream cs = cs(cur)) {
                    styler.drawText(cur.doc, cs, "Background step failed",
                            M, cur.y, styler.boldFont(), 8f, ColorScheme.FAILED_TEXT);
                }
                cur.advance(LMD);
                // CC1 fix: -1 → configured maxOutputLines
                renderer.renderErrorBlock(cur, err, -1);
            }
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
            if (hasLog) {
                renderer.renderLogs(cur, hook.getOutputLines(), "Hook output");
            }
            if (hasShot) {
                renderer.renderScreenshotGroup(cur, hook.getEmbeddings(), hasErr);
            }
        }
    }

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
                if (nml.length > 1 && !nml[1].isEmpty())
                    styler.drawText(cur.doc, cs, nml[1], nx, y - LMD,
                            styler.regularFont(), 9.5f, ColorScheme.TEXT_SECONDARY);
            } else {
                styler.dot(cs, M + 5f, y + 3f, 3.5f, ColorScheme.forStatus(st));
                styler.drawText(cur.doc, cs, kw, M + 14f, y,
                        styler.boldFont(), 9.5f, ColorScheme.ACCENT);
                float nx = M + 14f + kw.length() * 5.5f;
                styler.drawText(cur.doc, cs, nml[0], nx, y,
                        styler.regularFont(), 9.5f, ColorScheme.TEXT_SECONDARY);
                if (nml.length > 1 && !nml[1].isEmpty())
                    styler.drawText(cur.doc, cs, nml[1], nx, y - LMD,
                            styler.regularFont(), 9.5f, ColorScheme.TEXT_SECONDARY);
            }

            String ds = dur + "ms";
            styler.drawText(cur.doc, cs, ds, W - M - ds.length() * 5f, y,
                    styler.regularFont(), 7.5f, ColorScheme.TEXT_HINT);
            styler.hLine(cs, M, W - M, y - 5f, ColorScheme.BORDER_SUBTLE, 0.3f);
        }
        cur.advance(nml.length > 1 ? LMD * 2f : LMD);

        String err = step.getErrorMessage();
        if (err != null && !err.isEmpty()) {
            // CC1 fix: -1 → configured maxOutputLines
            renderer.renderErrorBlock(cur, err, -1);
        }
        if (!step.getOutputLines().isEmpty()) {
            renderer.renderLogs(cur, step.getOutputLines(), null);
        }
        // E2 fix: render DataTable and DocString in expanded mode too
        if (!step.getDataTableRows().isEmpty()) {
            renderer.renderDataTable(cur, step.getDataTableRows());
        }
        if (step.getDocString() != null && step.getDocString().getContent() != null) {
            renderer.renderDocString(cur, step.getDocString().getContent());
        }
        // E1 fix: show screenshots for ALL steps in expanded mode
        if (!step.getEmbeddings().isEmpty()) {
            renderer.renderScreenshotGroup(cur, step.getEmbeddings(), true);
        }
        cur.advance(4f);
    }

    private void drawSectionFooter(ConsolidatedPageCursor cur) throws IOException {
        try (PDPageContentStream cs = new PDPageContentStream(
                cur.doc, cur.page, PDPageContentStream.AppendMode.APPEND, true)) {
            float W = ConsolidatedPageCursor.PAGE_W;
            styler.hLine(cs, M, W - M, 28f, ColorScheme.BORDER, 0.4f);
            // D4: PluginVersion
            styler.drawText(cur.doc, cs,
                    PluginVersion.FULL + "  |  Expanded Steps",
                    M, 14f, styler.regularFont(), 7f, ColorScheme.TEXT_HINT);
        }
    }

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
