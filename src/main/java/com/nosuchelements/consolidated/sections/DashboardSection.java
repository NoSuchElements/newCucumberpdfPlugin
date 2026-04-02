package com.nosuchelements.consolidated.sections;

import com.nosuchelements.consolidated.ConsolidatedPageCursor;
import com.nosuchelements.consolidated.ReportStats;
import com.nosuchelements.consolidated.SectionHeader;
import com.nosuchelements.consolidated.TableOfContents;
import com.nosuchelements.cucumber.model.CucumberFeature;
import com.nosuchelements.pdf.ColorScheme;
import com.nosuchelements.pdf.PdfStyler;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Dashboard page — mirrors the grasshopper7 summary section:
 *
 * <pre>
 *  ╔══════════════════════════════════════════════════════╗
 *  ║  [ Report Title ]               [Overall: PASSED]   ║
 *  ║  Generated: 2025-01-15 14:32                         ║
 *  ╚══════════════════════════════════════════════════════╝
 *
 *  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐
 *  │  Features  │  │ Scenarios  │  │   Steps    │  │  Duration  │
 *  │     12     │  │    47      │  │   312      │  │   4m 32s   │
 *  │  10 passed │  │ 43 passed  │  │ 298 passed │  │            │
 *  └────────────┘  └────────────┘  └────────────┘  └────────────┘
 *
 *  Scenarios distribution ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 *  ● Passed 43   ● Failed 2   ● Skipped 2
 *
 *  Steps distribution ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 *  ● Passed 298  ● Failed 6   ● Skipped 8
 *
 *  Features at a glance (top 10 by failure)
 *  ┌──────────────────────────────────────────────────────┐
 *  │  Feature Name               Pass  Fail  Skip  Time  │
 *  ├──────────────────────────────────────────────────────┤
 *  │  …                                                   │
 *  └──────────────────────────────────────────────────────┘
 * </pre>
 */
public class DashboardSection {

    private static final float M    = ConsolidatedPageCursor.MARGIN_H;
    private static final float CW   = ConsolidatedPageCursor.CONTENT_W;
    private static final float CARD = 72f;
    private static final float GAP  = 8f;
    private static final float ROW  = 18f;

    private final PdfStyler s;
    private final String    reportTitle;
    private final String    tagPrefix;

    public DashboardSection(PdfStyler styler, String reportTitle, String tagPrefix) {
        this.s           = styler;
        this.reportTitle = reportTitle;
        this.tagPrefix   = tagPrefix;
    }

    public void build(PDDocument doc, PDPage firstPage,
                      List<CucumberFeature> features,
                      ReportStats stats,
                      TableOfContents toc) throws IOException {
        ConsolidatedPageCursor cur = new ConsolidatedPageCursor(
                doc, firstPage, s, "Dashboard");

        toc.add("Dashboard", cur.currentPageIndex());

        // -- Page background --
        try (PDPageContentStream cs = cs(cur)) {
            s.fillRect(cs, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight(),
                    ColorScheme.PAGE_BG);
        }

        // -- Report header band --
        drawReportHeader(cur, stats);

        cur.advance(16f);

        // -- Metric cards --
        drawMetricCards(cur, stats);

        cur.advance(20f);

        // -- Scenario distribution bar --
        drawDistributionBar(cur, "Scenarios Distribution",
                stats.passedScenarios, stats.failedScenarios, stats.skippedScenarios,
                stats.totalScenarios);

        cur.advance(14f);

        // -- Steps distribution bar --
        drawDistributionBar(cur, "Steps Distribution",
                stats.passedSteps, stats.failedSteps, stats.skippedSteps,
                stats.totalSteps);

        cur.advance(20f);

        // -- Features at a glance table (top 15) --
        drawFeaturesGlance(cur, features, stats);

        // -- Footer --
        drawFooter(cur);
    }

    // -----------------------------------------------------------------------
    // Report header
    // -----------------------------------------------------------------------

    private void drawReportHeader(ConsolidatedPageCursor cur,
                                   ReportStats stats) throws IOException {
        float W   = ConsolidatedPageCursor.PAGE_W;
        float hdrH = 62f;
        float top  = cur.y;
        float bandY = top - hdrH;
        String status  = stats.getOverallStatus();
        String ts = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm"));

        try (PDPageContentStream cs = cs(cur)) {
            s.fillRect(cs, 0, bandY, W, hdrH, ColorScheme.HEADER);
            s.fillRect(cs, 0, bandY, W, 4f, ColorScheme.forStatus(status));

            // Title
            s.drawText(cur.doc, cs, reportTitle,
                    M, top - 16f, s.boldFont(), 17f, ColorScheme.TEXT_WHITE);
            // Subtitle
            s.drawText(cur.doc, cs, "Generated: " + ts,
                    M, top - 35f, s.regularFont(), 9f, ColorScheme.TEXT_HINT);
            // Plugin tag
            s.drawText(cur.doc, cs, "Cucumber PDF Reporter  v1.2.0",
                    M, top - 50f, s.regularFont(), 7f, ColorScheme.TEXT_HINT);

            // Status badge (right)
            float bW = 80f, bH = 22f;
            float bX  = W - M - bW, bMid = bandY + hdrH / 2f;
            s.fillRect(cs, bX, bMid - bH / 2f, bW, bH, ColorScheme.forStatus(status));
            s.drawText(cur.doc, cs, status,
                    bX + 10f, bMid - 4f, s.boldFont(), 10f, ColorScheme.TEXT_WHITE);
        }
        cur.advance(hdrH);
    }

    // -----------------------------------------------------------------------
    // Metric cards
    // -----------------------------------------------------------------------

    private void drawMetricCards(ConsolidatedPageCursor cur,
                                  ReportStats stats) throws IOException {
        cur.ensureSpace(CARD + 8f);
        float cardW = (CW - 3 * GAP) / 4f;
        float cardY  = cur.y - CARD;

        int featPass = stats.passRatePercent(stats.passedFeatures, stats.totalFeatures);
        int scenPass = stats.passRatePercent(stats.passedScenarios, stats.totalScenarios);
        int stepPass = stats.passRatePercent(stats.passedSteps, stats.totalSteps);

        Object[][] cards = {
            {"Features",   str(stats.totalFeatures),
             stats.passedFeatures + " passed  " + stats.failedFeatures + " failed",
             featPass + "% pass rate"},
            {"Scenarios",  str(stats.totalScenarios),
             stats.passedScenarios + " passed  " + stats.failedScenarios + " failed",
             scenPass + "% pass rate"},
            {"Steps",      str(stats.totalSteps),
             stats.passedSteps + " passed  " + stats.failedSteps + " failed",
             stepPass + "% pass rate"},
            {"Duration",   stats.formatDuration(),
             "total execution time",
             stats.totalScenarios + " scenarios ran"},
        };

        try (PDPageContentStream cs = cs(cur)) {
            for (int i = 0; i < 4; i++) {
                float cx = M + i * (cardW + GAP);
                s.drawCard(cs, cx, cardY, cardW, CARD);
                // Accent top stripe
                s.fillRect(cs, cx, cardY + CARD - 3f, cardW, 3f,
                        i == 3 ? ColorScheme.ACCENT : ColorScheme.forStatus(
                                i == 0 ? (stats.failedFeatures > 0 ? "failed"
                                        : stats.skippedFeatures > 0 ? "skipped" : "passed") :
                                i == 1 ? (stats.failedScenarios > 0 ? "failed"
                                        : stats.skippedScenarios > 0 ? "skipped" : "passed") :
                                (stats.failedSteps > 0 ? "failed"
                                        : stats.skippedSteps > 0 ? "skipped" : "passed")));
                // Label
                s.drawText(cur.doc, cs, (String) cards[i][0],
                        cx + 10f, cardY + CARD - 14f, s.boldFont(), 8f, ColorScheme.TEXT_MUTED);
                // Big value
                s.drawText(cur.doc, cs, (String) cards[i][1],
                        cx + 10f, cardY + CARD - 34f, s.boldFont(), 20f, ColorScheme.TEXT_PRIMARY);
                // Row 1 sub
                s.drawText(cur.doc, cs, (String) cards[i][2],
                        cx + 10f, cardY + 22f, s.regularFont(), 7.5f, ColorScheme.TEXT_HINT);
                // Row 2 sub
                s.drawText(cur.doc, cs, (String) cards[i][3],
                        cx + 10f, cardY + 10f, s.regularFont(), 7.5f, ColorScheme.TEXT_HINT);
            }
        }
        cur.advance(CARD + 4f);
    }

    // -----------------------------------------------------------------------
    // Distribution bar + legend
    // -----------------------------------------------------------------------

    private void drawDistributionBar(ConsolidatedPageCursor cur,
                                      String label,
                                      int passed, int failed, int skipped,
                                      int total) throws IOException {
        cur.ensureSpace(40f);

        try (PDPageContentStream cs = cs(cur)) {
            // Section label
            s.drawText(cur.doc, cs, label, M, cur.y,
                    s.boldFont(), 9f, ColorScheme.TEXT_MUTED);
            float barY = cur.y - 12f;
            s.drawProgressBar(cs, M, barY, CW, 10f, passed, failed, skipped);

            // Legend
            float lx = M, ly = barY - 14f;
            lx = legendDot(cur.doc, cs, lx, ly, ColorScheme.PASSED,
                    "Passed " + passed + "  (" + pct(passed, total) + "%)");
            lx += 12f;
            lx = legendDot(cur.doc, cs, lx, ly, ColorScheme.FAILED,
                    "Failed " + failed + "  (" + pct(failed, total) + "%)");
            lx += 12f;
            legendDot(cur.doc, cs, lx, ly, ColorScheme.SKIPPED,
                    "Skipped " + skipped + "  (" + pct(skipped, total) + "%)");
        }
        cur.advance(38f);
    }

    private float legendDot(PDDocument doc, PDPageContentStream cs,
                             float x, float y,
                             java.awt.Color color, String label) throws IOException {
        s.dot(cs, x + 4f, y + 4f, 3.5f, color);
        s.drawText(doc, cs, label, x + 12f, y, s.regularFont(), 8f, ColorScheme.TEXT_MUTED);
        return x + 12f + label.length() * 4.3f;
    }

    // -----------------------------------------------------------------------
    // Features at a glance (mini table)
    // -----------------------------------------------------------------------

    private void drawFeaturesGlance(ConsolidatedPageCursor cur,
                                     List<CucumberFeature> features,
                                     ReportStats stats) throws IOException {
        int maxRows = Math.min(features.size(), 14);

        // Section label
        try (PDPageContentStream cs = cs(cur)) {
            s.drawText(cur.doc, cs, "Features at a Glance",
                    M, cur.y, s.boldFont(), 9f, ColorScheme.TEXT_MUTED);
        }
        cur.advance(12f);

        // Header row
        drawGlanceHeader(cur);

        // Data rows
        boolean alt = false;
        for (int i = 0; i < maxRows; i++) {
            cur.ensureSpace(ROW + 2f);
            CucumberFeature f = features.get(i);
            drawGlanceRow(cur, f, alt);
            alt = !alt;
        }
        if (features.size() > maxRows) {
            cur.ensureSpace(ROW);
            try (PDPageContentStream cs = cs(cur)) {
                s.drawText(cur.doc, cs,
                        "... and " + (features.size() - maxRows) + " more features (see Features section)",
                        M + 6f, cur.y - ROW + 6f,
                        s.italicFont(), 8f, ColorScheme.TEXT_HINT);
            }
            cur.advance(ROW);
        }
    }

    private void drawGlanceHeader(ConsolidatedPageCursor cur) throws IOException {
        cur.ensureSpace(ROW + 2f);
        try (PDPageContentStream cs = cs(cur)) {
            s.fillRect(cs, M, cur.y - ROW, CW, ROW, ColorScheme.HEADER);
            float hy = cur.y - ROW + 5f;
            s.drawText(cur.doc, cs, "Feature Name", M + 6f,        hy, s.boldFont(), 8f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Case ID",      M + CW * .44f, hy, s.boldFont(), 8f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Status",       M + CW * .56f, hy, s.boldFont(), 8f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Scen",         M + CW * .67f, hy, s.boldFont(), 8f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Progress",     M + CW * .75f, hy, s.boldFont(), 8f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Time",         M + CW * .90f, hy, s.boldFont(), 8f, ColorScheme.TEXT_HINT);
        }
        cur.advance(ROW + 1f);
    }

    private void drawGlanceRow(ConsolidatedPageCursor cur,
                                CucumberFeature f, boolean alt) throws IOException {
        String st     = f.getOverallStatus();
        String caseId = extractCaseId(f);
        long   dur    = 0;
        for (var sc : f.getActualScenarios()) dur += sc.getDurationMillis();

        try (PDPageContentStream cs = cs(cur)) {
            s.fillRect(cs, M, cur.y - ROW, CW, ROW,
                    alt ? ColorScheme.ROW_ALT : ColorScheme.CARD_BG);
            float ry = cur.y - ROW + 5f;

            // Status dot
            s.dot(cs, M + 8f, ry + 4f, 3.5f, ColorScheme.forStatus(st));
            // Feature name
            s.drawText(cur.doc, cs, trunc(safe(f.getName()), 38),
                    M + 18f, ry, s.regularFont(), 8.5f, ColorScheme.TEXT_SECONDARY);
            // Case ID
            s.drawText(cur.doc, cs, trunc(caseId, 12),
                    M + CW * .44f, ry, s.regularFont(), 7.5f, ColorScheme.TEXT_MUTED);
            // Status
            s.drawText(cur.doc, cs, st,
                    M + CW * .56f, ry, s.boldFont(), 7.5f, ColorScheme.textForStatus(st));
            // Scenarios fraction
            s.drawText(cur.doc, cs,
                    f.getPassedScenarios() + "/" + f.getTotalScenarios(),
                    M + CW * .67f, ry, s.regularFont(), 8f, ColorScheme.TEXT_SECONDARY);
            // Steps mini progress bar
            s.drawProgressBar(cs, M + CW * .75f, ry, CW * .13f, 5f,
                    f.getPassedSteps(), f.getFailedSteps(), f.getSkippedSteps());
            // Duration
            s.drawText(cur.doc, cs, fmtMs(dur),
                    M + CW * .90f, ry, s.regularFont(), 8f, ColorScheme.TEXT_MUTED);
            // Row divider
            s.hLine(cs, M, M + CW, cur.y - ROW, ColorScheme.BORDER_SUBTLE, 0.3f);
        }
        cur.advance(ROW);
    }

    /**
     * Extract a case ID from the feature's tags using the configured tagPrefix.
     *
     * Normalises both the prefix and each tag by stripping a leading '@' and
     * doing a case-insensitive comparison, so all of the following work:
     *   tagPrefix = "QTEST_TC_"  and tag = "@QTEST_TC_1001"  → "TC_1001"
     *   tagPrefix = "@QTEST_TC_" and tag = "qtest_tc_5050"   → "TC_5050"
     *
     * Returns "NA" when no matching tag is found or tagPrefix is blank.
     */
    private String extractCaseId(CucumberFeature f) {
        if (tagPrefix == null || tagPrefix.isBlank()) {
            return "NA";
        }
        // Normalise prefix: strip leading '@', uppercase
        String prefix = tagPrefix.startsWith("@")
                ? tagPrefix.substring(1).toUpperCase()
                : tagPrefix.toUpperCase();

        for (String tag : f.getTags()) {
            if (tag == null) continue;
            // Normalise tag: strip leading '@', uppercase
            String normalised = tag.startsWith("@")
                    ? tag.substring(1).toUpperCase()
                    : tag.toUpperCase();
            if (normalised.startsWith(prefix)) {
                // Return the numeric/identifier portion formatted as TC_####
                String remainder = normalised.substring(prefix.length());
                return "TC_" + remainder;
            }
        }
        return "NA";
    }

    // -----------------------------------------------------------------------
    // Footer
    // -----------------------------------------------------------------------

    private void drawFooter(ConsolidatedPageCursor cur) throws IOException {
        try (PDPageContentStream cs = new PDPageContentStream(
                cur.doc, cur.page, PDPageContentStream.AppendMode.APPEND, true)) {
            float W = ConsolidatedPageCursor.PAGE_W;
            s.hLine(cs, M, W - M, 28f, ColorScheme.BORDER, 0.5f);
            s.drawText(cur.doc, cs,
                    "Cucumber PDF Reporter v1.2.0  |  Apache PDFBox  |  Page 1 — Dashboard",
                    M, 14f, s.regularFont(), 7f, ColorScheme.TEXT_HINT);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private PDPageContentStream cs(ConsolidatedPageCursor cur) throws IOException {
        return new PDPageContentStream(cur.doc, cur.page,
                PDPageContentStream.AppendMode.APPEND, true);
    }
    private static String safe(String v)        { return v != null ? v : ""; }
    private static String str(int n)            { return String.valueOf(n); }
    private static String trunc(String v, int n) {
        if (v == null) return "";
        return v.length() > n ? v.substring(0, n - 3) + "..." : v;
    }
    private static String fmtMs(long ms) {
        if (ms < 1000) return ms + "ms";
        return String.format("%.1fs", ms / 1000.0);
    }
    private static int pct(int v, int total) {
        if (total <= 0) return 0;
        return (int) Math.round(100.0 * v / total);
    }
}
