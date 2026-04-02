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
import java.util.List;

/**
 * Scenarios section — all scenarios across all features, grouped by feature.
 *
 * <h3>Fixes applied</h3>
 * <ul>
 *   <li><b>S1</b> – Tag truncation now applied <em>per-tag</em> inside
 *       {@link #buildTagLine} before the overflow-count suffix is appended,
 *       so the "+N" suffix is never silently clipped.</li>
 *   <li><b>S2</b> – For Scenario Outline examples, the tags column shows
 *       "—" instead of repeating the identical parent-outline tag list on
 *       every example row, reducing visual noise.</li>
 *   <li><b>D4</b> – Section footer uses {@link PluginVersion#FULL}.</li>
 * </ul>
 */
public class ScenariosSection {

    private static final float M    = ConsolidatedPageCursor.MARGIN_H;
    private static final float CW   = ConsolidatedPageCursor.CONTENT_W;
    private static final float FHDR = 18f;
    private static final float CHDR = 17f;
    private static final float ROW  = 18f;

    /** Maximum characters shown for a single tag in the Tags column. */
    private static final int MAX_TAG_CHARS = 16;

    private static final float C_IDX  = M + 6f;
    private static final float C_NAME = M + 24f;
    private static final float C_TAGS = M + CW * 0.40f;
    private static final float C_ST   = M + CW * 0.54f;
    private static final float C_STEP = M + CW * 0.63f;
    private static final float C_BAR  = M + CW * 0.72f;
    private static final float C_TIME = M + CW * 0.88f;

    private final PdfStyler s;

    public ScenariosSection(PdfStyler styler) { this.s = styler; }

    public void build(PDDocument doc, PDPage firstPage,
                      List<CucumberFeature> features,
                      TableOfContents toc) throws IOException {

        int totalScenarios = features.stream()
                .mapToInt(CucumberFeature::getTotalScenarios).sum();

        ConsolidatedPageCursor cur = new ConsolidatedPageCursor(
                doc, firstPage, s, "Scenarios");

        toc.add("Scenarios", cur.currentPageIndex());

        SectionHeader.draw(cur, s, "Scenarios",
                totalScenarios + " scenario" + (totalScenarios == 1 ? "" : "s"),
                ColorScheme.PASSED);

        for (CucumberFeature feature : features) {
            List<CucumberScenario> scenarios = feature.getActualScenarios();
            if (scenarios == null || scenarios.isEmpty()) continue;

            cur.ensureSpace(FHDR + CHDR + ROW + 4f);
            drawFeatureSubHeader(cur, feature, scenarios.size());
            drawColumnHeader(cur);

            boolean alt = false;
            int idx = 1;
            // S2: track the previous scenario's tag line to detect outline examples
            String prevTagLine = null;
            for (CucumberScenario sc : scenarios) {
                cur.ensureSpace(ROW + 2f);
                drawScenarioRow(cur, sc, idx++, alt, prevTagLine);
                prevTagLine = buildTagLine(sc.getTags());
                alt = !alt;
            }
            cur.advance(8f);
        }

        drawSectionFooter(cur);
    }

    private void drawFeatureSubHeader(ConsolidatedPageCursor cur,
                                       CucumberFeature feature,
                                       int scenCount) throws IOException {
        String name = trunc(safe(feature.getName()), 55);
        String st   = feature.getOverallStatus();
        try (PDPageContentStream cs = cs(cur)) {
            s.fillRect(cs, M, cur.y - FHDR, CW, FHDR, ColorScheme.bgForStatus(st));
            s.fillRect(cs, M, cur.y - FHDR, 4f, FHDR, ColorScheme.forStatus(st));
            s.strokeRect(cs, M, cur.y - FHDR, CW, FHDR, ColorScheme.BORDER, 0.5f);
            float fy = cur.y - FHDR + 5f;
            s.drawText(cur.doc, cs, name, M + 12f, fy,
                    s.boldFont(), 9.5f, ColorScheme.TEXT_PRIMARY);
            String badge = scenCount + " scenario" + (scenCount == 1 ? "" : "s");
            s.drawText(cur.doc, cs, badge,
                    M + CW - badge.length() * 4.8f - 6f, fy,
                    s.regularFont(), 8f, ColorScheme.TEXT_MUTED);
        }
        cur.advance(FHDR);
    }

    private void drawColumnHeader(ConsolidatedPageCursor cur) throws IOException {
        try (PDPageContentStream cs = cs(cur)) {
            s.fillRect(cs, M, cur.y - CHDR, CW, CHDR, ColorScheme.HEADER);
            float hy = cur.y - CHDR + 4f;
            s.drawText(cur.doc, cs, "#",        C_IDX,  hy, s.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Scenario", C_NAME, hy, s.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Tags",     C_TAGS, hy, s.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Status",   C_ST,   hy, s.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Steps",    C_STEP, hy, s.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Progress", C_BAR,  hy, s.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Duration", C_TIME, hy, s.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
        }
        cur.advance(CHDR);
    }

    private void drawScenarioRow(ConsolidatedPageCursor cur,
                                  CucumberScenario sc,
                                  int idx, boolean alt,
                                  String prevTagLine) throws IOException {
        String st      = sc.getStatus();
        // S2: show "—" for example rows that share the same tags as the outline above
        String tagLine = buildTagLine(sc.getTags());
        String tagDisplay = tagLine.equals(prevTagLine) && prevTagLine != null && !prevTagLine.isEmpty()
                ? "—" : tagLine;

        try (PDPageContentStream cs = cs(cur)) {
            float bgY = cur.y - ROW;
            s.fillRect(cs, M, bgY, CW, ROW,
                    alt ? ColorScheme.ROW_ALT : ColorScheme.CARD_BG);
            float ry = cur.y - ROW + 4f;
            s.dot(cs, M + 8f, ry + 5f, 3f, ColorScheme.forStatus(st));
            s.drawText(cur.doc, cs, str(idx), C_IDX, ry,
                    s.regularFont(), 7.5f, ColorScheme.TEXT_MUTED);
            s.drawText(cur.doc, cs, trunc(safe(sc.getName()), 30),
                    C_NAME, ry, s.regularFont(), 9f, ColorScheme.TEXT_SECONDARY);
            if (!tagDisplay.isEmpty()) {
                s.drawText(cur.doc, cs, tagDisplay,
                        C_TAGS, ry, s.italicFont(), 7.5f, ColorScheme.TEXT_MUTED);
            }
            s.drawText(cur.doc, cs, st, C_ST, ry,
                    s.boldFont(), 8f, ColorScheme.textForStatus(st));
            s.drawText(cur.doc, cs,
                    sc.getPassedSteps() + "/" + sc.getTotalSteps(),
                    C_STEP, ry, s.regularFont(), 8f, ColorScheme.TEXT_SECONDARY);
            s.drawProgressBar(cs, C_BAR, ry, CW * 0.13f, 5f,
                    sc.getPassedSteps(), sc.getFailedSteps(), sc.getSkippedSteps());
            s.drawText(cur.doc, cs, sc.formatDuration(),
                    C_TIME, ry, s.regularFont(), 8f, ColorScheme.TEXT_MUTED);
            s.hLine(cs, M, M + CW, bgY, ColorScheme.BORDER_SUBTLE, 0.3f);
        }
        cur.advance(ROW);
    }

    private void drawSectionFooter(ConsolidatedPageCursor cur) throws IOException {
        try (PDPageContentStream cs = cs(cur)) {
            styler_hline(cur, cs);
            cur.advance(6f);
        }
        try (PDPageContentStream cs = cs(cur)) {
            s.drawText(cur.doc, cs,
                    PluginVersion.FULL + "  |  Scenarios",
                    M, 14f, s.regularFont(), 7f, ColorScheme.TEXT_HINT);
        }
    }

    private void styler_hline(ConsolidatedPageCursor cur, PDPageContentStream cs) throws IOException {
        s.hLine(cs, M, M + CW, cur.y, ColorScheme.BORDER, 0.4f);
    }

    /**
     * Build a compact tag display string.
     *
     * <p>S1 fix: each individual tag is truncated to {@value #MAX_TAG_CHARS} chars
     * before assembly, so the "+N more" overflow suffix is never cut off.</p>
     *
     * @param tags raw tag list (may include leading {@code @})
     * @return display string, e.g. {@code "smoke  regression  +2"}
     */
    static String buildTagLine(List<String> tags) {
        if (tags == null || tags.isEmpty()) return "";
        final int limit = 2;
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (String tag : tags) {
            if (shown >= limit) {
                sb.append(" +").append(tags.size() - limit);
                break;
            }
            if (sb.length() > 0) sb.append("  ");
            // S1 fix: truncate per-tag before adding to the assembled line
            String display = tag.startsWith("@") ? tag.substring(1) : tag;
            if (display.length() > MAX_TAG_CHARS) {
                display = display.substring(0, MAX_TAG_CHARS - 2) + "..";
            }
            sb.append(display);
            shown++;
        }
        return sb.toString();
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
}
