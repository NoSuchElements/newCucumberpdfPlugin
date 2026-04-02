package com.nosuchelements.consolidated.sections;

import com.nosuchelements.consolidated.ConsolidatedPageCursor;
import com.nosuchelements.consolidated.PluginVersion;
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
 * Tag Statistics section — per-tag pass/fail/skip breakdown.
 *
 * <h3>Fixes applied</h3>
 * <ul>
 *   <li><b>T1</b> – Empty-tags path now creates a proper cursor and calls
 *       {@link SectionHeader#draw} so the page has a consistent section header,
 *       continuation-banner capability, and a registered page-number slot.
 *       The early-return that bypassed all of this is removed.</li>
 *   <li><b>T2</b> – Tags matching the configured {@code testCaseTagPrefix}
 *       (default {@code QTEST_TC_}) are sorted into a separate group at the
 *       <em>bottom</em> of the table so execution-category tags ({@code @smoke},
 *       {@code @regression}) remain at the top.</li>
 *   <li><b>D4</b> – Section footer uses {@link PluginVersion#FULL}.</li>
 * </ul>
 */
public class TagStatsSection {

    private static final float M    = ConsolidatedPageCursor.MARGIN_H;
    private static final float CW   = ConsolidatedPageCursor.CONTENT_W;
    private static final float HDR  = 18f;
    private static final float ROW  = 17f;

    private static final float C_TAG  = M + 6f;
    private static final float C_TOT  = M + CW * 0.42f;
    private static final float C_PASS = M + CW * 0.52f;
    private static final float C_FAIL = M + CW * 0.62f;
    private static final float C_SKIP = M + CW * 0.72f;
    private static final float C_BAR  = M + CW * 0.80f;

    /** Default prefix used to identify test-case-ID tags (e.g. QTEST_TC_). */
    private static final String DEFAULT_TC_PREFIX = "QTEST_TC_";

    private final PdfStyler styler;
    /** Upper-cased, @-stripped prefix used to detect test-case-ID tags. */
    private final String    tcPrefix;

    public TagStatsSection(PdfStyler styler) {
        this(styler, DEFAULT_TC_PREFIX);
    }

    public TagStatsSection(PdfStyler styler, String testCaseTagPrefix) {
        this.styler   = styler;
        String raw = (testCaseTagPrefix != null && !testCaseTagPrefix.isBlank())
                ? testCaseTagPrefix.strip() : DEFAULT_TC_PREFIX;
        this.tcPrefix = raw.startsWith("@") ? raw.substring(1).toUpperCase() : raw.toUpperCase();
    }

    // -----------------------------------------------------------------------

    public void build(PDDocument doc, PDPage firstPage,
                      List<CucumberFeature> features,
                      TableOfContents toc) throws IOException {

        Map<String, TagStat> tagMap = new LinkedHashMap<>();
        collectTagStats(features, tagMap);

        // T1 fix: always create a cursor so the page has proper structure
        ConsolidatedPageCursor cur = new ConsolidatedPageCursor(
                doc, firstPage, styler, "Tag Statistics");

        toc.add("Tag Statistics", cur.currentPageIndex());

        SectionHeader.draw(cur, styler,
                "Tag Statistics",
                tagMap.isEmpty() ? "no tags found"
                        : tagMap.size() + " unique tag" + (tagMap.size() == 1 ? "" : "s"),
                ColorScheme.SKIPPED);

        if (tagMap.isEmpty()) {
            // T1 fix: render notice through the cursor (continuation-capable)
            cur.ensureSpace(40f);
            try (PDPageContentStream cs = cs(cur)) {
                styler.drawText(cur.doc, cs,
                        "No tags were found in this test run.",
                        M, cur.y, styler.regularFont(), 11f, ColorScheme.TEXT_MUTED);
            }
            cur.advance(20f);
            drawSectionFooter(cur, Collections.emptyList());
            return;
        }

        // T2 fix: split into two groups — regular tags first, TC-ID tags last
        List<TagStat> regular = new ArrayList<>();
        List<TagStat> tcIds   = new ArrayList<>();
        for (TagStat t : tagMap.values()) {
            String normalised = t.name.startsWith("@")
                    ? t.name.substring(1).toUpperCase() : t.name.toUpperCase();
            if (normalised.startsWith(tcPrefix)) tcIds.add(t);
            else                                  regular.add(t);
        }

        // Sort each group: failing-first, then by total desc
        Comparator<TagStat> cmp = Comparator
                .<TagStat>comparingInt(t -> -t.failed)
                .thenComparingInt(t -> -t.total);
        regular.sort(cmp);
        tcIds.sort(cmp);

        List<TagStat> sorted = new ArrayList<>(regular.size() + tcIds.size());
        sorted.addAll(regular);
        sorted.addAll(tcIds);

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
        java.awt.Color rowStatus = tag.failed  > 0 ? ColorScheme.FAILED
                : tag.skipped > 0 ? ColorScheme.SKIPPED
                : ColorScheme.PASSED;

        try (PDPageContentStream cs = cs(cur)) {
            float bgY = cur.y - ROW;
            styler.fillRect(cs, M, bgY, CW, ROW,
                    alt ? ColorScheme.ROW_ALT : ColorScheme.CARD_BG);
            styler.fillRect(cs, M, bgY, 2.5f, ROW, rowStatus);
            float ry = cur.y - ROW + 4f;

            styler.drawText(cur.doc, cs, trunc(tag.name, 36),
                    C_TAG + 4f, ry, styler.regularFont(), 8.5f, ColorScheme.TEXT_SECONDARY);
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
            float barW = CW * 0.16f;
            styler.drawProgressBar(cs, C_BAR, ry, barW, 5f,
                    tag.passed, tag.failed, tag.skipped);
            int pct = tag.total > 0 ? (int) Math.round(100.0 * tag.passed / tag.total) : 0;
            styler.drawText(cur.doc, cs, pct + "%",
                    C_BAR + barW + 6f, ry, styler.regularFont(), 7.5f, ColorScheme.TEXT_HINT);
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
            // D4: PluginVersion
            styler.drawText(cur.doc, cs,
                    PluginVersion.FULL + "  |  Tag Statistics",
                    M, 14f, styler.regularFont(), 7f, ColorScheme.TEXT_HINT);
        }
        cur.advance(18f);
    }

    // -----------------------------------------------------------------------

    private static class TagStat {
        final String name;
        int total, passed, failed, skipped;
        TagStat(String name) { this.name = name; }
    }

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
