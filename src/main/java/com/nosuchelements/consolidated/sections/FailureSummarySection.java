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
import java.util.ArrayList;
import java.util.List;

/**
 * Failure Summary section — shows only FAILED and SKIPPED scenarios with their
 * full step detail and error messages.
 *
 * This is the most useful CI-facing section: a triage page at the top of the
 * report listing every broken scenario with enough context to diagnose the
 * failure without scrolling through the full Detailed section.
 *
 * Activated by {@code displayFailureSummary=true} in the Mojo configuration.
 * Placed as the second section in the report (right after Dashboard) so it is
 * the first thing a reader sees after the overview.
 *
 * Layout per failing scenario:
 *   ┌────────────────────────────────────────────────────────────┐
 *   │  Feature Name              [FAILED]   2 failures  1 skip  │
 *   │  Scenario Name                              [FAILED] 1.2s │
 *   ├────────────────────────────────────────────────────────────┤
 *   │  ● Given  I navigate to checkout               100ms      │
 *   │  ● When   I enter an expired card               400ms     │
 *   │    ┌─────────────────────────────────────────────────┐    │
 *   │    │ PaymentException: Card expired               │    │
 *   │    │ at PaymentService.charge(PaymentService:88)  │    │
 *   │    └─────────────────────────────────────────────────┘    │
 *   │  ○ Then   I see the declined message            skipped   │
 *   └────────────────────────────────────────────────────────────┘
 *
 * If all scenarios passed, a green "All scenarios passed" banner is shown
 * instead of an empty section.
 */
public class FailureSummarySection {

    private static final float M    = ConsolidatedPageCursor.MARGIN_H;
    private static final float CW   = ConsolidatedPageCursor.CONTENT_W;
    private static final float FHDR = 20f;   // feature group header
    private static final float SHDR = 34f;   // scenario header band
    private static final float LMD  = 15f;
    private static final float LSM  = 11f;

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

        // Collect only FAILED and SKIPPED scenarios, grouped by feature
        List<FeatureFailures> groups = collectFailures(features);
        int totalFailures = groups.stream()
                .mapToInt(g -> g.scenarios.size()).sum();

        ConsolidatedPageCursor cur = new ConsolidatedPageCursor(
                doc, firstPage, styler, "Failure Summary");

        toc.add("Failure Summary", cur.currentPageIndex());

        SectionHeader.draw(cur, styler,
                "Failure Summary",
                totalFailures == 0
                        ? "all scenarios passed"
                        : totalFailures + " failing scenario" + (totalFailures == 1 ? "" : "s"),
                ColorScheme.FAILED);

        if (totalFailures == 0) {
            drawAllPassedBanner(cur);
            return;
        }

        for (FeatureFailures group : groups) {
            drawFeatureGroupHeader(cur, group);
            for (CucumberScenario sc : group.scenarios) {
                cur.ensureSpace(SHDR + LMD * 2);
                drawFailingScenarioHeader(cur, sc);
                drawFailingSteps(cur, sc);
                cur.advance(10f);
            }
            cur.advance(6f);
        }
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
                if ("FAILED".equals(st) || "SKIPPED".equals(st)) {
                    bad.add(sc);
                }
            }
            if (!bad.isEmpty()) {
                groups.add(new FeatureFailures(feature, bad));
            }
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
                .filter(s -> "FAILED".equals(s.getStatus())).count();
        long skipped = group.scenarios.stream()
                .filter(s -> "SKIPPED".equals(s.getStatus())).count();

        try (PDPageContentStream cs = cs(cur)) {
            styler.fillRect(cs, M, cur.y - FHDR, CW, FHDR, ColorScheme.FAILED_BG);
            styler.fillRect(cs, M, cur.y - FHDR, 4f, FHDR, ColorScheme.FAILED);
            styler.strokeRect(cs, M, cur.y - FHDR, CW, FHDR, ColorScheme.BORDER, 0.4f);

            float fy = cur.y - FHDR + 5f;
            styler.drawText(cur.doc, cs,
                    trunc(safe(group.feature.getName()), 52),
                    M + 12f, fy, styler.boldFont(), 9.5f, ColorScheme.TEXT_PRIMARY);

            // Right-side counts
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
    // Step listing — show all steps; expand errors for failed ones only
    // -----------------------------------------------------------------------

    private void drawFailingSteps(ConsolidatedPageCursor cur,
                                   CucumberScenario sc) throws IOException {
        // Before-hook errors
        for (CucumberStep hook : sc.getBeforeHooks()) {
            String err = hook.getErrorMessage();
            if (err != null && !err.isEmpty()) {
                drawHookErrorLabel(cur, "Before hook failed");
                renderer.renderErrorBlock(cur, err, 6);
            }
        }

        for (CucumberStep step : sc.getSteps()) {
            drawStepLine(cur, step);
            // Expand error for failed steps — this is the money line for triage
            String err = step.getErrorMessage();
            if (err != null && !err.isEmpty()) {
                renderer.renderErrorBlock(cur, err, 10);
            }
            // Show output logs on failing step too
            if (!step.getOutputLines().isEmpty() && "failed".equalsIgnoreCase(step.getStatus())) {
                renderer.renderLogs(cur, step.getOutputLines(), null);
            }
        }

        // After-hook errors
        for (CucumberStep hook : sc.getAfterHooks()) {
            String err = hook.getErrorMessage();
            if (err != null && !err.isEmpty()) {
                drawHookErrorLabel(cur, "After hook failed");
                renderer.renderErrorBlock(cur, err, 6);
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

            // Status label for non-passed steps (skip "passed" to reduce noise)
            if (!"passed".equalsIgnoreCase(st)) {
                styler.drawText(cur.doc, cs, st.toLowerCase(),
                        W - M - st.length() * 5f - 6f, y,
                        styler.boldFont(), 7.5f, ColorScheme.textForStatus(st));
            } else {
                String ds = dur + "ms";
                styler.drawText(cur.doc, cs, ds,
                        W - M - ds.length() * 5f, y,
                        styler.regularFont(), 7.5f, ColorScheme.TEXT_HINT);
            }

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
    // Data holder
    // -----------------------------------------------------------------------

    private static class FeatureFailures {
        final CucumberFeature          feature;
        final List<CucumberScenario>   scenarios;
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
