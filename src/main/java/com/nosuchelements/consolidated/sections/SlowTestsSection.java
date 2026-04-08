package com.nosuchelements.consolidated.sections;

import com.nosuchelements.consolidated.ConsolidatedPageCursor;
import com.nosuchelements.cucumber.model.CucumberFeature;
import com.nosuchelements.cucumber.model.CucumberScenario;
import com.nosuchelements.pdf.ColorScheme;
import com.nosuchelements.pdf.PdfStyler;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Slow Tests section -- top-N slowest scenarios sorted by duration descending.
 *
 * <p>Controlled by {@code displaySlowTests=true} and {@code slowTestTopN} (default 15).
 * This section is invaluable for CI pipeline performance tuning: it highlights
 * which scenarios are consuming the most execution time and are candidates for
 * parallelisation or optimisation.</p>
 *
 * <pre>
 * +----------------------------------------------------------------------+
 * |  #  |  Scenario                    |  Feature          |  Duration  |
 * +----------------------------------------------------------------------+
 * |  1  |  Full checkout flow          |  Shopping Cart    |   12.4s    |
 * |  2  |  Login with LDAP             |  Authentication   |    8.7s    |
 * |  ...                                                                 |
 * +----------------------------------------------------------------------+
 * </pre>
 */
public class SlowTestsSection {

    private static final float M    = ConsolidatedPageCursor.MARGIN_H;
    private static final float CW   = ConsolidatedPageCursor.CONTENT_W;
    private static final float ROW  = 18f;
    private static final float HDR  = 18f;

    private static final float C_IDX  = M + 6f;
    private static final float C_NAME = M + 26f;
    private static final float C_FEAT = M + CW * 0.52f;
    private static final float C_ST   = M + CW * 0.75f;
    private static final float C_DUR  = M + CW * 0.86f;

    private final PdfStyler s;
    private final int       topN;

    public SlowTestsSection(PdfStyler styler, int topN) {
        this.s    = styler;
        this.topN = Math.max(1, Math.min(topN, 50));
    }

    public void build(PDDocument doc, PDPage firstPage,
                      List<CucumberFeature> features) throws IOException {

        // Collect all scenarios with their parent feature
        List<ScenarioEntry> all = new ArrayList<>();
        for (CucumberFeature f : features) {
            for (CucumberScenario sc : f.getActualScenarios()) {
                all.add(new ScenarioEntry(f, sc));
            }
        }

        // Sort by duration descending, take top N
        List<ScenarioEntry> top = all.stream()
                .sorted(Comparator.comparingLong((ScenarioEntry e) -> e.sc.getDurationMillis()).reversed())
                .limit(topN)
                .collect(Collectors.toList());

        if (top.isEmpty()) return;

        ConsolidatedPageCursor cur = new ConsolidatedPageCursor(doc, firstPage, s, "Slow Tests");

        // Section header -- ASCII-only title (no Unicode em-dash)
        try (PDPageContentStream cs = cs(cur)) {
            float W = ConsolidatedPageCursor.PAGE_W;
            s.fillRect(cs, 0, cur.y - 36f, W, 36f, ColorScheme.HEADER);
            s.fillRect(cs, 0, cur.y - 36f, W, 3f, ColorScheme.SKIPPED);
            s.drawText(cur.doc, cs, "Slow Tests - Top " + top.size(),
                    M, cur.y - 24f, s.boldFont(), 14f, ColorScheme.TEXT_WHITE);
            String sub = "sorted by duration descending";
            s.drawText(cur.doc, cs, sub,
                    W - M - sub.length() * 4.8f, cur.y - 24f,
                    s.regularFont(), 9f, ColorScheme.TEXT_HINT);
        }
        cur.advance(44f);

        // Column header
        drawColumnHeader(cur);

        boolean alt = false;
        int rank = 1;
        for (ScenarioEntry e : top) {
            cur.ensureSpace(ROW + 2f);
            drawRow(cur, e, rank++, alt);
            alt = !alt;
        }

        // Total time of top N
        long totalMs = top.stream().mapToLong(e -> e.sc.getDurationMillis()).sum();
        cur.ensureSpace(24f);
        try (PDPageContentStream cs = cs(cur)) {
            s.hLine(cs, M, M + CW, cur.y, ColorScheme.BORDER, 0.5f);
            cur.advance(6f);
            String summary = "Top " + top.size() + " slowest scenarios account for "
                    + fmtMs(totalMs) + " of total execution time";
            s.drawText(cur.doc, cs, summary,
                    M, cur.y - 10f, s.regularFont(), 8.5f, ColorScheme.TEXT_SECONDARY);
        }
    }

    private void drawColumnHeader(ConsolidatedPageCursor cur) throws IOException {
        cur.ensureSpace(HDR + 2f);
        try (PDPageContentStream cs = cs(cur)) {
            s.fillRect(cs, M, cur.y - HDR, CW, HDR, ColorScheme.HEADER);
            float hy = cur.y - HDR + 4f;
            s.drawText(cur.doc, cs, "#",        C_IDX,  hy, s.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Scenario", C_NAME, hy, s.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Feature",  C_FEAT, hy, s.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Status",   C_ST,   hy, s.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Duration", C_DUR,  hy, s.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
        }
        cur.advance(HDR + 1f);
    }

    private void drawRow(ConsolidatedPageCursor cur, ScenarioEntry e,
                         int rank, boolean alt) throws IOException {
        String st  = e.sc.getStatus();
        long   dur = e.sc.getDurationMillis();

        try (PDPageContentStream cs = cs(cur)) {
            float bgY = cur.y - ROW;
            s.fillRect(cs, M, bgY, CW, ROW,
                    alt ? ColorScheme.ROW_ALT : ColorScheme.CARD_BG);

            float ry = cur.y - ROW + 5f;

            // Rank badge
            s.fillRect(cs, C_IDX - 2f, bgY + 3f, 16f, 12f,
                    rank <= 3 ? ColorScheme.SKIPPED : ColorScheme.BORDER);
            s.drawText(cur.doc, cs, String.valueOf(rank),
                    C_IDX + 1f, ry, s.boldFont(), 8f, ColorScheme.TEXT_WHITE);

            // Status dot
            s.dot(cs, C_NAME - 8f, ry + 4f, 3f, ColorScheme.forStatus(st));

            // Scenario name
            s.drawText(cur.doc, cs, trunc(safe(e.sc.getName()), 36),
                    C_NAME, ry, s.regularFont(), 8.5f, ColorScheme.TEXT_SECONDARY);

            // Feature name
            s.drawText(cur.doc, cs, trunc(safe(e.feature.getName()), 28),
                    C_FEAT, ry, s.regularFont(), 8f, ColorScheme.TEXT_MUTED);

            // Status
            s.drawText(cur.doc, cs, st, C_ST, ry,
                    s.boldFont(), 8f, ColorScheme.textForStatus(st));

            // Duration -- bold, coloured by severity
            java.awt.Color durColor = dur > 10_000 ? ColorScheme.FAILED
                    : dur > 5_000  ? ColorScheme.SKIPPED
                    : ColorScheme.TEXT_SECONDARY;
            s.drawText(cur.doc, cs, fmtMs(dur), C_DUR, ry,
                    s.boldFont(), 9f, durColor);

            s.hLine(cs, M, M + CW, bgY, ColorScheme.BORDER_SUBTLE, 0.3f);
        }
        cur.advance(ROW);
    }

    // -----------------------------------------------------------------------

    private static class ScenarioEntry {
        final CucumberFeature  feature;
        final CucumberScenario sc;
        ScenarioEntry(CucumberFeature f, CucumberScenario s) {
            this.feature = f; this.sc = s;
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
    private static String fmtMs(long ms) {
        if (ms < 1_000) return ms + "ms";
        if (ms < 60_000) return String.format("%.1fs", ms / 1000.0);
        return (ms / 60_000) + "m " + ((ms % 60_000) / 1000) + "s";
    }
}
