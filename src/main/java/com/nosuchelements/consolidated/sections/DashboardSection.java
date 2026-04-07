package com.nosuchelements.consolidated.sections;

import com.nosuchelements.consolidated.ConsolidatedPageCursor;
import com.nosuchelements.consolidated.ReportStats;
import com.nosuchelements.consolidated.SectionHeader;
import com.nosuchelements.consolidated.TableOfContents;
import com.nosuchelements.cucumber.model.CucumberFeature;
import com.nosuchelements.cucumber.model.ReportMetadata;
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
 * Dashboard page — single-page overview of the entire test run.
 *
 * <h3>Layout</h3>
 * <pre>
 *  ┌──────────────────────────────────────────────────────────────────────┐
 *  │  [Report Title]                                   [PASSED / FAILED]  │
 *  │  Generated: 2025-06-10  14:32  •  Cucumber PDF Reporter v1.5.0       │
 *  └──────────────────────────────────────────────────────────────────────┘
 *
 *  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐
 *  │  Features  │  │ Scenarios  │  │   Steps    │  │  Duration  │
 *  │     12     │  │    47      │  │   312      │  │   4m 32s   │
 *  └────────────┘  └────────────┘  └────────────┘  └────────────┘
 *
 *  Scenarios Distribution ━━━━━━━━━━━━━━━━━━━━━━━━━━━━  91%
 *  Steps Distribution     ━━━━━━━━━━━━━━━━━━━━━━━━━━━━  95%
 *
 *  [Environment Metadata block — if configured]
 *
 *  Features at a Glance
 *  # │ Feature │ Case ID │ Status │ Scenarios │ Progress │ Duration
 * </pre>
 *
 * <h3>v1.5.0 additions</h3>
 * <ul>
 *   <li>Environment / build metadata block (when configured)</li>
 *   <li>Accurate pass-rate percentage labels on distribution bars</li>
 *   <li>Version string updated to v1.5.0</li>
 *   <li>Footer text aligned with page-number stamp (avoids overlap)</li>
 * </ul>
 */
public class DashboardSection {

    private static final float M    = ConsolidatedPageCursor.MARGIN_H;
    private static final float CW   = ConsolidatedPageCursor.CONTENT_W;
    private static final float CARD = 72f;
    private static final float GAP  = 8f;
    private static final float ROW  = 18f;
    static final String VERSION = "1.5.0";

    private final PdfStyler     s;
    private final String        reportTitle;
    private final String        tagPrefix;
    private final ReportMetadata metadata;

    public DashboardSection(PdfStyler styler, String reportTitle, String tagPrefix) {
        this(styler, reportTitle, tagPrefix, null);
    }

    public DashboardSection(PdfStyler styler, String reportTitle,
                             String tagPrefix, ReportMetadata metadata) {
        this.s           = styler;
        this.reportTitle = reportTitle != null ? reportTitle : "Cucumber Test Report";
        this.tagPrefix   = tagPrefix   != null ? tagPrefix   : "QTEST_TC_";
        this.metadata    = metadata;
    }

    public void build(PDDocument doc, PDPage firstPage,
                      List<CucumberFeature> features,
                      ReportStats stats,
                      TableOfContents toc) throws IOException {

        ConsolidatedPageCursor cur = new ConsolidatedPageCursor(
                doc, firstPage, s, "Dashboard");
        toc.add("Dashboard", cur.currentPageIndex());

        // Page background
        try (PDPageContentStream cs = cs(cur)) {
            s.fillRect(cs, 0, 0,
                    PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight(),
                    ColorScheme.PAGE_BG);
        }

        drawReportHeader(cur, stats);
        cur.advance(16f);

        drawMetricCards(cur, stats);
        cur.advance(20f);

        drawDistributionBar(cur, "Scenarios Distribution",
                stats.passedScenarios, stats.failedScenarios,
                stats.skippedScenarios, stats.totalScenarios);
        cur.advance(14f);

        drawDistributionBar(cur, "Steps Distribution",
                stats.passedSteps, stats.failedSteps,
                stats.skippedSteps, stats.totalSteps);
        cur.advance(20f);

        // Environment metadata block (v1.5.0)
        if (metadata != null && !metadata.isEmpty()) {
            drawMetadataBlock(cur);
            cur.advance(8f);
        }

        drawFeaturesGlance(cur, features);
        drawFooter(cur);
    }

    // -----------------------------------------------------------------------
    // Report header
    // -----------------------------------------------------------------------

    private void drawReportHeader(ConsolidatedPageCursor cur,
                                   ReportStats stats) throws IOException {
        float W     = ConsolidatedPageCursor.PAGE_W;
        float hdrH  = 64f;
        float top   = cur.y;
        float bandY = top - hdrH;
        String status = stats.getOverallStatus();
        String ts = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm"));

        try (PDPageContentStream cs = cs(cur)) {
            s.fillRect(cs, 0, bandY, W, hdrH, ColorScheme.HEADER);
            s.fillRect(cs, 0, bandY, W, 4f, ColorScheme.forStatus(status));

            // Title (adaptive size)
            float tf = reportTitle.length() > 50 ? 14f : reportTitle.length() > 35 ? 16f : 18f;
            s.drawText(cur.doc, cs, reportTitle,
                    M, top - 18f, s.boldFont(), tf, ColorScheme.TEXT_WHITE);
            s.drawText(cur.doc, cs, "Generated: " + ts
                    + "   \u2022   Cucumber PDF Reporter v" + VERSION,
                    M, top - 37f, s.regularFont(), 8.5f, ColorScheme.TEXT_HINT);

            // Overall status badge
            float bW = 84f, bH = 24f;
            float bX = W - M - bW, bMid = bandY + hdrH / 2f;
            s.fillRect(cs, bX, bMid - bH / 2f, bW, bH, ColorScheme.forStatus(status));
            s.drawText(cur.doc, cs, status,
                    bX + 10f, bMid - 5f, s.boldFont(), 11f, ColorScheme.TEXT_WHITE);

            // Pass rate below badge
            int passRate = stats.passRatePercent(stats.passedSteps, stats.totalSteps);
            s.drawText(cur.doc, cs, passRate + "% steps passed",
                    bX + 4f, bandY + 8f, s.regularFont(), 7.5f, ColorScheme.TEXT_HINT);
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
        float cardY = cur.y - CARD;

        int featPass = stats.passRatePercent(stats.passedFeatures, stats.totalFeatures);
        int scenPass = stats.passRatePercent(stats.passedScenarios, stats.totalScenarios);
        int stepPass = stats.passRatePercent(stats.passedSteps,     stats.totalSteps);

        String[][] cards = {
            {"Features",  str(stats.totalFeatures),
             stats.passedFeatures + " passed  " + stats.failedFeatures + " failed",
             featPass + "% pass rate"},
            {"Scenarios", str(stats.totalScenarios),
             stats.passedScenarios + " passed  " + stats.failedScenarios + " failed",
             scenPass + "% pass rate"},
            {"Steps",     str(stats.totalSteps),
             stats.passedSteps + " passed  " + stats.failedSteps + " failed",
             stepPass + "% pass rate"},
            {"Duration",  stats.formatDuration(),
             "total execution time",
             stats.totalScenarios + " scenarios"},
        };

        java.awt.Color[] stripes = {
            stats.failedFeatures  > 0 ? ColorScheme.FAILED
                : stats.skippedFeatures  > 0 ? ColorScheme.SKIPPED : ColorScheme.PASSED,
            stats.failedScenarios > 0 ? ColorScheme.FAILED
                : stats.skippedScenarios > 0 ? ColorScheme.SKIPPED : ColorScheme.PASSED,
            stats.failedSteps     > 0 ? ColorScheme.FAILED
                : stats.skippedSteps     > 0 ? ColorScheme.SKIPPED : ColorScheme.PASSED,
            ColorScheme.ACCENT,
        };

        try (PDPageContentStream cs = cs(cur)) {
            for (int i = 0; i < 4; i++) {
                float cx = M + i * (cardW + GAP);
                s.drawCard(cs, cx, cardY, cardW, CARD);
                s.fillRect(cs, cx, cardY + CARD - 3f, cardW, 3f, stripes[i]);
                s.drawText(cur.doc, cs, cards[i][0],
                        cx + 10f, cardY + CARD - 14f, s.boldFont(), 8f, ColorScheme.TEXT_MUTED);
                s.drawText(cur.doc, cs, cards[i][1],
                        cx + 10f, cardY + CARD - 34f, s.boldFont(), 20f, ColorScheme.TEXT_PRIMARY);
                s.drawText(cur.doc, cs, cards[i][2],
                        cx + 10f, cardY + 22f, s.regularFont(), 7.5f, ColorScheme.TEXT_HINT);
                s.drawText(cur.doc, cs, cards[i][3],
                        cx + 10f, cardY + 10f, s.regularFont(), 7.5f, ColorScheme.TEXT_HINT);
            }
        }
        cur.advance(CARD + 4f);
    }

    // -----------------------------------------------------------------------
    // Distribution bar
    // -----------------------------------------------------------------------

    private void drawDistributionBar(ConsolidatedPageCursor cur, String label,
                                      int passed, int failed, int skipped,
                                      int total) throws IOException {
        cur.ensureSpace(42f);
        try (PDPageContentStream cs = cs(cur)) {
            s.drawText(cur.doc, cs, label, M, cur.y,
                    s.boldFont(), 9f, ColorScheme.TEXT_MUTED);

            // Pass rate on right
            int pct = total > 0 ? (int) Math.round(100.0 * passed / total) : 0;
            String pctLabel = pct + "%";
            s.drawText(cur.doc, cs, pctLabel,
                    M + CW - pctLabel.length() * 5f, cur.y,
                    s.boldFont(), 9f, pct >= 90 ? ColorScheme.PASSED
                            : pct >= 70 ? ColorScheme.SKIPPED : ColorScheme.FAILED);

            float barY = cur.y - 13f;
            s.drawProgressBar(cs, M, barY, CW - 40f, 10f, passed, failed, skipped);

            // Legend
            float lx = M, ly = barY - 16f;
            lx = legendDot(cur.doc, cs, lx, ly, ColorScheme.PASSED,
                    "Passed " + passed + "  (" + pct(passed, total) + "%)");
            lx += 12f;
            lx = legendDot(cur.doc, cs, lx, ly, ColorScheme.FAILED,
                    "Failed " + failed + "  (" + pct(failed, total) + "%)");
            lx += 12f;
            legendDot(cur.doc, cs, lx, ly, ColorScheme.SKIPPED,
                    "Skipped " + skipped + "  (" + pct(skipped, total) + "%)");
        }
        cur.advance(42f);
    }

    private float legendDot(PDDocument doc, PDPageContentStream cs,
                             float x, float y, java.awt.Color color,
                             String label) throws IOException {
        s.dot(cs, x + 4f, y + 4f, 3.5f, color);
        s.drawText(doc, cs, label, x + 12f, y, s.regularFont(), 8f, ColorScheme.TEXT_MUTED);
        return x + 12f + label.length() * 4.3f;
    }

    // -----------------------------------------------------------------------
    // Environment metadata (v1.5.0)
    // -----------------------------------------------------------------------

    private void drawMetadataBlock(ConsolidatedPageCursor cur) throws IOException {
        if (metadata == null || metadata.isEmpty()) return;
        MetadataSection ms = new MetadataSection(s);
        ms.draw(cur, metadata);
    }

    // -----------------------------------------------------------------------
    // Features at a glance
    // -----------------------------------------------------------------------

    private void drawFeaturesGlance(ConsolidatedPageCursor cur,
                                     List<CucumberFeature> features) throws IOException {
        int maxRows = Math.min(features.size(), 14);

        try (PDPageContentStream cs = cs(cur)) {
            s.drawText(cur.doc, cs, "Features at a Glance",
                    M, cur.y, s.boldFont(), 9f, ColorScheme.TEXT_MUTED);
        }
        cur.advance(12f);

        drawGlanceHeader(cur);

        boolean alt = false;
        for (int i = 0; i < maxRows; i++) {
            cur.ensureSpace(ROW + 2f);
            drawGlanceRow(cur, features.get(i), i + 1, alt);
            alt = !alt;
        }
        if (features.size() > maxRows) {
            cur.ensureSpace(ROW);
            try (PDPageContentStream cs = cs(cur)) {
                s.drawText(cur.doc, cs,
                        "... and " + (features.size() - maxRows)
                                + " more features — see Features section",
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
            s.drawText(cur.doc, cs, "Feature",   M + 6f,        hy, s.boldFont(), 8f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Case ID",   M + CW * .44f, hy, s.boldFont(), 8f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Status",    M + CW * .56f, hy, s.boldFont(), 8f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Scen",      M + CW * .67f, hy, s.boldFont(), 8f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Progress",  M + CW * .75f, hy, s.boldFont(), 8f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Time",      M + CW * .90f, hy, s.boldFont(), 8f, ColorScheme.TEXT_HINT);
        }
        cur.advance(ROW + 1f);
    }

    private void drawGlanceRow(ConsolidatedPageCursor cur, CucumberFeature f,
                                int idx, boolean alt) throws IOException {
        String st     = f.getOverallStatus();
        String caseId = extractCaseId(f);
        long   dur    = f.getActualScenarios().stream()
                         .mapToLong(sc -> sc.getDurationMillis()).sum();

        try (PDPageContentStream cs = cs(cur)) {
            s.fillRect(cs, M, cur.y - ROW, CW, ROW,
                    alt ? ColorScheme.ROW_ALT : ColorScheme.CARD_BG);
            float ry = cur.y - ROW + 5f;

            s.dot(cs, M + 8f, ry + 4f, 3.5f, ColorScheme.forStatus(st));
            s.drawText(cur.doc, cs, trunc(safe(f.getName()), 38),
                    M + 18f, ry, s.regularFont(), 8.5f, ColorScheme.TEXT_SECONDARY);
            s.drawText(cur.doc, cs, trunc(caseId, 12),
                    M + CW * .44f, ry, s.regularFont(), 7.5f,
                    "\u2014".equals(caseId) ? ColorScheme.TEXT_HINT : ColorScheme.ACCENT);
            s.drawText(cur.doc, cs, st, M + CW * .56f, ry,
                    s.boldFont(), 7.5f, ColorScheme.textForStatus(st));
            s.drawText(cur.doc, cs,
                    f.getPassedScenarios() + "/" + f.getTotalScenarios(),
                    M + CW * .67f, ry, s.regularFont(), 8f, ColorScheme.TEXT_SECONDARY);
            s.drawProgressBar(cs, M + CW * .75f, ry, CW * .13f, 5f,
                    f.getPassedSteps(), f.getFailedSteps(), f.getSkippedSteps());
            s.drawText(cur.doc, cs, fmtMs(dur),
                    M + CW * .90f, ry, s.regularFont(), 8f, ColorScheme.TEXT_MUTED);
            s.hLine(cs, M, M + CW, cur.y - ROW, ColorScheme.BORDER_SUBTLE, 0.3f);
        }
        cur.advance(ROW);
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
                    "Cucumber PDF Reporter v" + VERSION
                            + "  |  Apache PDFBox  |  Dashboard",
                    M, 14f, s.regularFont(), 7f, ColorScheme.TEXT_HINT);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String extractCaseId(CucumberFeature f) {
        String tag = f.extractQtestTag(tagPrefix);
        if ("UNKNOWN".equals(tag)) return "\u2014";
        String stripped = tag.toUpperCase().startsWith(tagPrefix.toUpperCase())
                ? tag.substring(tagPrefix.length()) : tag;
        return "TC-" + stripped;
    }

    private PDPageContentStream cs(ConsolidatedPageCursor cur) throws IOException {
        return new PDPageContentStream(cur.doc, cur.page,
                PDPageContentStream.AppendMode.APPEND, true);
    }
    private static String safe(String v)         { return v != null ? v : ""; }
    private static String str(int n)             { return String.valueOf(n); }
    private static String trunc(String v, int n) {
        if (v == null) return "";
        return v.length() > n ? v.substring(0, n - 3) + "..." : v;
    }
    private static String fmtMs(long ms) {
        if (ms < 1000) return ms + "ms";
        return String.format("%.1fs", ms / 1000.0);
    }
    private static int pct(int v, int total) {
        return total <= 0 ? 0 : (int) Math.round(100.0 * v / total);
    }
}
