package com.nosuchelements.consolidated.sections;

import com.nosuchelements.consolidated.ConsolidatedPageCursor;
import com.nosuchelements.consolidated.SectionHeader;
import com.nosuchelements.consolidated.TableOfContents;
import com.nosuchelements.cucumber.model.CucumberFeature;
import com.nosuchelements.cucumber.model.CucumberScenario;
import com.nosuchelements.pdf.ColorScheme;
import com.nosuchelements.pdf.PdfStyler;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import java.io.IOException;
import java.util.*;

/**
 * Tag Statistics section — mirrors the grasshopper7 "tags" section.
 *
 * <p>Scans all features and scenarios for tags, then produces a sorted table
 * showing each unique tag alongside its scenario pass/fail/skip counts and
 * an inline progress bar.</p>
 *
 * <h3>Example output</h3>
 * <pre>
 * ╔════════════════════════════════════════════════════════╗
 * ║  TAG STATISTICS                          18 unique tags║
 * ╚════════════════════════════════════════════════════════╝
 *
 * │ Tag              │ Total │ Pass │ Fail │ Skip │ Progress         │
 * │ @smoke           │  8    │   7  │   1  │   0  │ ███████░         │
 * │ @regression      │ 15    │  14  │   1  │   0  │ ██████████████░  │
 * │ @QTEST_TC_1001   │  1    │   1  │   0  │   0  │ ████████████████ │
 * ...
 * </pre>
 *
 * <p>Tags are sorted: failing tags first (by fail count desc), then passing
 * by total scenarios desc. This surfaces broken tags immediately.</p>
 */
public class TagStatsSection {

    private static final float M    = ConsolidatedPageCursor.MARGIN_H;
    private static final float CW   = ConsolidatedPageCursor.CONTENT_W;
    private static final float HDR  = 18f;
    private static final float ROW  = 17f;

    // Column offsets
    private static final float C_TAG  = M + 6f;
    private static final float C_TOT  = M + CW * 0.42f;
    private static final float C_PASS = M + CW * 0.52f;
    private static final float C_FAIL = M + CW * 0.62f;
    private static final float C_SKIP = M + CW * 0.72f;
    private static final float C_BAR  = M + CW * 0.80f;

    private final PdfStyler styler;

    public TagStatsSection(PdfStyler styler) {
        this.styler = styler;
    }

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public void build(PDDocument doc, PDPage firstPage,
                      List<CucumberFeature> features,
                      TableOfContents toc) throws IOException {

        // --- Aggregate tag stats ---
        Map<String, TagStat> tagMap = new LinkedHashMap<>();
        collectTagStats(features, tagMap);

        // If no tags: render a notice on the pre-allocated page rather than leaving it blank.
        // cur hasn't been created yet here, so we write directly to firstPage.
        if (tagMap.isEmpty()) {
            float noticeY = firstPage.getMediaBox().getUpperRightY()
                    - ConsolidatedPageCursor.MARGIN_V - 20f;
            try (PDPageContentStream cs = new PDPageContentStream(
                    doc, firstPage, PDPageContentStream.AppendMode.APPEND, true)) {
                styler.drawText(doc, cs,
                        "No tags were found in this test run.",
                        M, noticeY, styler.regularFont(), 11f, ColorScheme.TEXT_MUTED);
            }
            toc.add("Tag Statistics", doc.getNumberOfPages());
            return;
        }

        // Sort: failing tags first, then by total desc
        List<TagStat> sorted = new ArrayList<>(tagMap.values());
        sorted.sort(Comparator
                .<TagStat>comparingInt(t -> -t.failed)
                .thenComparingInt(t -> -t.total));

        ConsolidatedPageCursor cur = new ConsolidatedPageCursor(
                doc, firstPage, styler, "Tag Statistics");

        toc.add("Tag Statistics", cur.currentPageIndex());

        SectionHeader.draw(cur, styler,
                "Tag Statistics",
                tagMap.size() + " unique tag" + (tagMap.size() == 1 ? "" : "s"),
                ColorScheme.SKIPPED);

        drawColumnHeader(cur);

        boolean alt = false;
        for (TagStat tag : sorted) {
            cur.ensureSpace(ROW + 2f);
            drawTagRow(cur, tag, alt);
            alt = !alt;
        }

        drawSectionFooter(cur, sorted);
    }

    // -----------------------------------------------------------------------
    // Data collection
    // -----------------------------------------------------------------------

    private void collectTagStats(List<CucumberFeature> features,
                                  Map<String, TagStat> tagMap) {
        for (CucumberFeature feature : features) {
            for (CucumberScenario sc : feature.getActualScenarios()) {
                String status = sc.getStatus();
                // Merge feature-level tags and scenario-level tags
                Set<String> tags = new LinkedHashSet<>(feature.getTags());
                tags.addAll(sc.getTags());
                for (String tag : tags) {
                    if (tag == null || tag.isBlank()) continue;
                    TagStat stat = tagMap.computeIfAbsent(tag, TagStat::new);
                    stat.total++;
                    if      ("FAILED".equalsIgnoreCase(status))  stat.failed++;
                    else if ("SKIPPED".equalsIgnoreCase(status)) stat.skipped++;
                    else                                          stat.passed++;
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Drawing
    // -----------------------------------------------------------------------

    private void drawColumnHeader(ConsolidatedPageCursor cur) throws IOException {
        cur.ensureSpace(HDR + 2f);
        try (PDPageContentStream cs = cs(cur)) {
            styler.fillRect(cs, M, cur.y - HDR, CW, HDR, ColorScheme.HEADER);
            float hy = cur.y - HDR + 4f;
            styler.drawText(cur.doc, cs, "Tag",      C_TAG,  hy, styler.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
            styler.drawText(cur.doc, cs, "Total",    C_TOT,  hy, styler.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
            styler.drawText(cur.doc, cs, "Pass",     C_PASS, hy, styler.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
            styler.drawText(cur.doc, cs, "Fail",     C_FAIL, hy, styler.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
            styler.drawText(cur.doc, cs, "Skip",     C_SKIP, hy, styler.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
            styler.drawText(cur.doc, cs, "Progress", C_BAR,  hy, styler.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
        }
        cur.advance(HDR + 1f);
    }

    private void drawTagRow(ConsolidatedPageCursor cur,
                             TagStat tag, boolean alt) throws IOException {
        // Determine row status colour
        java.awt.Color rowStatus = tag.failed > 0   ? ColorScheme.FAILED
                : tag.skipped > 0 ? ColorScheme.SKIPPED
                : ColorScheme.PASSED;

        try (PDPageContentStream cs = cs(cur)) {
            float bgY = cur.y - ROW;
            styler.fillRect(cs, M, bgY, CW, ROW,
                    alt ? ColorScheme.ROW_ALT : ColorScheme.CARD_BG);
            // Left status stripe (2.5px)
            styler.fillRect(cs, M, bgY, 2.5f, ROW, rowStatus);

            float ry = cur.y - ROW + 4f;

            // Tag name
            styler.drawText(cur.doc, cs, trunc(tag.name, 36),
                    C_TAG + 4f, ry, styler.regularFont(), 8.5f, ColorScheme.TEXT_SECONDARY);

            // Counts
            styler.drawText(cur.doc, cs, str(tag.total),
                    C_TOT, ry, styler.boldFont(), 8.5f, ColorScheme.TEXT_PRIMARY);
            styler.drawText(cur.doc, cs, str(tag.passed),
                    C_PASS, ry, styler.regularFont(), 8.5f, ColorScheme.PASSED_TEXT);
            if (tag.failed > 0) {
                styler.drawText(cur.doc, cs, str(tag.failed),
                        C_FAIL, ry, styler.boldFont(), 8.5f, ColorScheme.FAILED_TEXT);
            } else {
                styler.drawText(cur.doc, cs, "—",
                        C_FAIL, ry, styler.regularFont(), 8.5f, ColorScheme.TEXT_HINT);
            }
            if (tag.skipped > 0) {
                styler.drawText(cur.doc, cs, str(tag.skipped),
                        C_SKIP, ry, styler.regularFont(), 8.5f, ColorScheme.SKIPPED_TEXT);
            } else {
                styler.drawText(cur.doc, cs, "—",
                        C_SKIP, ry, styler.regularFont(), 8.5f, ColorScheme.TEXT_HINT);
            }

            // Mini progress bar
            float barW = CW * 0.16f;
            styler.drawProgressBar(cs, C_BAR, ry, barW, 5f,
                    tag.passed, tag.failed, tag.skipped);

            // Pass rate label
            int pct = tag.total > 0 ? (int) Math.round(100.0 * tag.passed / tag.total) : 0;
            styler.drawText(cur.doc, cs, pct + "%",
                    C_BAR + barW + 6f, ry, styler.regularFont(), 7.5f, ColorScheme.TEXT_HINT);

            // Row divider
            styler.hLine(cs, M, M + CW, bgY, ColorScheme.BORDER_SUBTLE, 0.3f);
        }
        cur.advance(ROW);
    }

    private void drawSectionFooter(ConsolidatedPageCursor cur,
                                    List<TagStat> sorted) throws IOException {
        long failingTags = sorted.stream().filter(t -> t.failed > 0).count();
        cur.ensureSpace(20f);
        try (PDPageContentStream cs = cs(cur)) {
            styler.hLine(cs, M, M + CW, cur.y, ColorScheme.BORDER, 0.5f);
            cur.advance(6f);
            String summary = sorted.size() + " tags  —  " + failingTags + " with failures";
            styler.drawText(cur.doc, cs, summary,
                    M, cur.y - 10f, styler.boldFont(), 8.5f, ColorScheme.TEXT_SECONDARY);
        }
        cur.advance(18f);
    }

    // -----------------------------------------------------------------------
    // Data class
    // -----------------------------------------------------------------------

    private static class TagStat {
        final String name;
        int total, passed, failed, skipped;

        TagStat(String name) { this.name = name; }
    }

    // -----------------------------------------------------------------------
    private PDPageContentStream cs(ConsolidatedPageCursor cur) throws IOException {
        return new PDPageContentStream(cur.doc, cur.page,
                PDPageContentStream.AppendMode.APPEND, true);
    }
    private static String str(int n)            { return String.valueOf(n); }
    private static String trunc(String v, int n) {
        if (v == null) return "";
        return v.length() > n ? v.substring(0, n - 3) + "..." : v;
    }
}
