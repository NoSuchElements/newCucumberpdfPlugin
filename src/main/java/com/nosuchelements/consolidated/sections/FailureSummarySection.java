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
import java.util.ArrayList;
import java.util.List;

/**
 * Failure Summary section — shows only FAILED and SKIPPED scenarios with their
 * full step detail, error messages, and any embedded screenshots.
 *
 * <h3>Fixes applied</h3>
 * <ul>
 *   <li><b>F1</b> – SKIPPED step screenshots no longer rendered; guard added so
 *       embeddings are only shown when the step actually failed.</li>
 *   <li><b>F2</b> – Section subtitle now accurately reflects failing vs skipped
 *       counts: e.g. "3 failing / 2 skipped" instead of always "N failing".</li>
 *   <li><b>F3</b> – Duration shown on <em>all</em> step lines (not just passed);
 *       failed/skipped steps now show both a coloured status label and the
 *       duration, giving triage context.</li>
 *   <li><b>D4</b> – Footer uses {@link PluginVersion#FULL} constant.</li>
 * </ul>
 */
public class FailureSummarySection {

    private static final float M    = ConsolidatedPageCursor.MARGIN_H;
    private static final float CW   = ConsolidatedPageCursor.CONTENT_W;
    private static final float FHDR = 20f;
    private static final float SHDR = 34f;
    private static final float LMD  = 15f;

    private final PdfStyler            styler;
    private final ContentBlockRenderer renderer;

    public FailureSummarySection(PdfStyler styler, int maxOutputLines) {
        this.styler   = styler;
        this.renderer = new ContentBlockRenderer(styler, maxOutputLines);
    }

    // -----------------------------------------------------------------------

    public void build(PDDocument doc, PDPage firstPage,
                      List<CucumberFeature> features,
                      TableOfContents toc) throws IOException {

        List<FeatureFailures> groups = collectFailures(features);
        long totalFailed  = groups.stream().flatMap(g -> g.scenarios.stream())
                .filter(s -> "FAILED".equalsIgnoreCase(s.getStatus())).count();
        long totalSkipped = groups.stream().flatMap(g -> g.scenarios.stream())
                .filter(s -> "SKIPPED".equalsIgnoreCase(s.getStatus())).count();
        int totalBad = (int)(totalFailed + totalSkipped);

        ConsolidatedPageCursor cur = new ConsolidatedPageCursor(
                doc, firstPage, styler, "Failure Summary");

        toc.add("Failure Summary", cur.currentPageIndex());

        // F2 fix: accurate subtitle
        String subtitle;
        if (totalBad == 0) {
            subtitle = "all scenarios passed";
        } else {
            subtitle = totalFailed + " failing";
            if (totalSkipped > 0) subtitle += "  /  " + totalSkipped + " skipped";
        }

        SectionHeader.draw(cur, styler, "Failure Summary", subtitle, ColorScheme.FAILED);

        if (totalBad == 0) {
            drawAllPassedBanner(cur);
            return;
        }

        for (FeatureFailures group : groups) {
            drawFeatureGroupHeader(cur, group);
            for (CucumberScenario sc : group.scenarios) {
                cur.ensureSpace(SHDR + LMD * 2);
                drawFailingScenarioHeader(cur, sc);
                cur.advance(6f);
                drawFailingSteps(cur, sc);
                cur.advance(10f);
            }
            cur.advance(6f);
        }

        drawSectionFooter(cur);
    }

    // -----------------------------------------------------------------------
    // Collection
    // -----------------------------------------------------------------------

    private List<FeatureFailures> collectFailures(List<CucumberFeature> features) {
        List<FeatureFailures> groups = new ArrayList<>();
        for (CucumberFeature feature : features) {
            List<CucumberScenario> bad = new ArrayList<>();
            for (CucumberScenario sc : feature.getActualScenarios()) {
                String st = sc.getStatus();
                if ("FAILED".equalsIgnoreCase(st) || "SKIPPED".equalsIgnoreCase(st)) {
                    bad.add(sc);
                }
            }
            if (!bad.isEmpty()) groups.add(new FeatureFailures(feature, bad));
        }
        return groups;
    }

    // -----------------------------------------------------------------------
    // All-pass banner
    // -----------------------------------------------------------------------

    private void drawAllPassedBanner(ConsolidatedPageCursor cur) throws IOException {
        cur.ensureSpace(50f);
        float bH = 36f;
        try (PDPageContentStream cs = cs(cur)) {
            styler.fillRect(cs, M, cur.y - bH, CW, bH, ColorScheme.PASSED_BG);
            styler.fillRect(cs, M, cur.y - bH, 4f, bH, ColorScheme.PASSED);
            styler.strokeRect(cs, M, cur.y - bH, CW, bH, ColorScheme.BORDER, 0.4f);
            styler.dot(cs, M + 18f, cur.y - bH / 2f + 1f, 5f, ColorScheme.PASSED);
            styler.drawText(cur.doc, cs,
                    "All scenarios passed in this test run.",
                    M + 32f, cur.y - bH / 2f - 4f,
                    styler.boldFont(), 11f, ColorScheme.PASSED_TEXT);
        }
        cur.advance(bH + 8f);
    }

    // -----------------------------------------------------------------------
    // Feature group header
    // -----------------------------------------------------------------------

    private void drawFeatureGroupHeader(ConsolidatedPageCursor cur,
                                         FeatureFailures group) throws IOException {
        cur.ensureSpace(FHDR + SHDR + LMD * 2);
        long failed  = group.scenarios.stream()
                .filter(s -> "FAILED".equalsIgnoreCase(s.getStatus())).count();
        long skipped = group.scenarios.stream()
                .filter(s -> "SKIPPED".equalsIgnoreCase(s.getStatus())).count();

        try (PDPageContentStream cs = cs(cur)) {
            styler.fillRect(cs, M, cur.y - FHDR, CW, FHDR, ColorScheme.FAILED_BG);
            styler.fillRect(cs, M, cur.y - FHDR, 4f, FHDR, ColorScheme.FAILED);
            styler.strokeRect(cs, M, cur.y - FHDR, CW, FHDR, ColorScheme.BORDER, 0.4f);
            float fy = cur.y - FHDR + 5f;
            styler.drawText(cur.doc, cs,
                    trunc(safe(group.feature.getName()), 52),
                    M + 12f, fy, styler.boldFont(), 9.5f, ColorScheme.TEXT_PRIMARY);
            // F2 fix: show counts per group too
            String counts = failed + " failed";
            if (skipped > 0) counts += "   " + skipped + " skipped";
            styler.drawText(cur.doc, cs, counts,
                    M + CW - counts.length() * 5f - 6f, fy,
                    styler.regularFont(), 8f, ColorScheme.FAILED_TEXT);
        }
        cur.advance(FHDR + 4f);
    }

    // -----------------------------------------------------------------------
    // Scenario header band
    // -----------------------------------------------------------------------

    private void drawFailingScenarioHeader(ConsolidatedPageCursor cur,
                                            CucumberScenario sc) throws IOException {
        String st   = sc.getStatus();
        String name = trunc(safe(sc.getName()), 68);

        try (PDPageContentStream cs = cs(cur)) {
            styler.fillRect(cs, M, cur.y - SHDR, CW, SHDR, ColorScheme.HEADER);
            styler.fillRect(cs, M, cur.y - SHDR, CW, 3f, ColorScheme.forStatus(st));
            styler.drawText(cur.doc, cs, "Scenario",
                    M + 8f, cur.y - 11f,
                    styler.regularFont(), 7f, ColorScheme.TEXT_HINT);
            styler.drawText(cur.doc, cs, name,
                    M + 8f, cur.y - 26f,
                    styler.boldFont(), 11f, ColorScheme.TEXT_WHITE);
            float bW = 66f, bH = 17f;
            float bX = M + CW - bW, bMid = cur.y - SHDR + SHDR / 2f;
            styler.fillRect(cs, bX, bMid - bH / 2f, bW, bH, ColorScheme.forStatus(st));
            styler.drawText(cur.doc, cs, st,
                    bX + 6f, bMid - 4f, styler.boldFont(), 8.5f, ColorScheme.TEXT_WHITE);
            String dur = sc.formatDuration();
            styler.drawText(cur.doc, cs, dur,
                    bX - dur.length() * 5.2f - 8f, bMid - 4f,
                    styler.regularFont(), 8f, ColorScheme.TEXT_HINT);
        }
        cur.advance(SHDR + 4f);
    }

    // -----------------------------------------------------------------------
    // Step listing
    // -----------------------------------------------------------------------

    private void drawFailingSteps(ConsolidatedPageCursor cur,
                                   CucumberScenario sc) throws IOException {
        for (CucumberStep hook : sc.getBeforeHooks()) {
            String err = hook.getErrorMessage();
            boolean hasErr  = err != null && !err.isEmpty();
            boolean hasShot = !hook.getEmbeddings().isEmpty();
            if (hasErr) {
                drawHookErrorLabel(cur, "Before hook failed");
                renderer.renderErrorBlock(cur, err, -1);
            }
            if (hasShot) {
                renderer.renderScreenshotGroup(cur, hook.getEmbeddings(), hasErr);
            }
        }

        for (CucumberStep step : sc.getSteps()) {
            drawStepLine(cur, step);
            String err = step.getErrorMessage();
            if (err != null && !err.isEmpty()) {
                renderer.renderErrorBlock(cur, err, -1);
            }
            // Show output logs only on failed steps
            if (!step.getOutputLines().isEmpty() && "failed".equalsIgnoreCase(step.getStatus())) {
                renderer.renderLogs(cur, step.getOutputLines(), null);
            }
            // F1 fix: only render screenshots for actually-failed steps
            if (!step.getEmbeddings().isEmpty() && "failed".equalsIgnoreCase(step.getStatus())) {
                renderer.renderScreenshotGroup(cur, step.getEmbeddings(), true);
            }
        }

        for (CucumberStep hook : sc.getAfterHooks()) {
            String err = hook.getErrorMessage();
            boolean hasErr  = err != null && !err.isEmpty();
            boolean hasShot = !hook.getEmbeddings().isEmpty();
            if (hasErr) {
                drawHookErrorLabel(cur, "After hook failed");
                renderer.renderErrorBlock(cur, err, -1);
            }
            if (hasShot) {
                renderer.renderScreenshotGroup(cur, hook.getEmbeddings(), hasErr);
            }
        }
    }

    private void drawStepLine(ConsolidatedPageCursor cur,
                               CucumberStep step) throws IOException {
        String kw  = safe(step.getKeyword());
        String nm  = safe(step.getName());
        String st  = step.getStatus();
        long   dur = step.getDurationMillis();
        boolean cont = ContentBlockRenderer.isContinuationKeyword(kw);

        int      avail = ContentBlockRenderer.availStepChars(kw.length(), cont);
        String[] nml   = ContentBlockRenderer.wrapStepName(nm, avail);
        float    h     = nml.length > 1 ? LMD * 2f + 3f : LMD + 3f;
        cur.ensureSpace(h);

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

            // F3 fix: always show duration; add status label for non-passed steps
            String ds = dur + "ms";
            float durX = W - M - ds.length() * 5f;
            if (!"passed".equalsIgnoreCase(st)) {
                // Status label to the left of duration
                String stLabel = st.toLowerCase();
                float stX = durX - stLabel.length() * 5f - 8f;
                styler.drawText(cur.doc, cs, stLabel, stX, y,
                        styler.boldFont(), 7.5f, ColorScheme.textForStatus(st));
            }
            styler.drawText(cur.doc, cs, ds, durX, y,
                    styler.regularFont(), 7.5f, ColorScheme.TEXT_HINT);

            styler.hLine(cs, M, W - M, y - 5f, ColorScheme.BORDER_SUBTLE, 0.3f);
        }
        cur.advance(nml.length > 1 ? LMD * 2f : LMD);
    }

    private void drawHookErrorLabel(ConsolidatedPageCursor cur,
                                     String label) throws IOException {
        cur.ensureSpace(LMD + 4f);
        try (PDPageContentStream cs = cs(cur)) {
            styler.drawText(cur.doc, cs, label, M, cur.y,
                    styler.boldFont(), 8.5f, ColorScheme.FAILED_TEXT);
        }
        cur.advance(LMD);
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
                    PluginVersion.FULL + "  |  Failure Summary",
                    M, 14f, styler.regularFont(), 7f, ColorScheme.TEXT_HINT);
        }
    }

    // -----------------------------------------------------------------------
    // Data holder
    // -----------------------------------------------------------------------

    private static class FeatureFailures {
        final CucumberFeature        feature;
        final List<CucumberScenario> scenarios;
        FeatureFailures(CucumberFeature f, List<CucumberScenario> s) {
            this.feature   = f;
            this.scenarios = s;
        }
    }

    // -----------------------------------------------------------------------
    private PDPageContentStream cs(ConsolidatedPageCursor cur) throws IOException {
        return new PDPageContentStream(cur.doc, cur.page,
                PDPageContentStream.AppendMode.APPEND, true);
    }
    private static String safe(String v) { return v != null ? v : ""; }
    private static String trunc(String v, int n) {
        if (v == null) return "";
        return v.length() > n ? v.substring(0, n - 3) + "..." : v;
    }
}
