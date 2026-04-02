package com.nosuchelements.consolidated.sections;

import com.nosuchelements.consolidated.ConsolidatedPageCursor;
import com.nosuchelements.consolidated.FeatureUtils;
import com.nosuchelements.consolidated.PluginVersion;
import com.nosuchelements.consolidated.SectionHeader;
import com.nosuchelements.consolidated.TableOfContents;
import com.nosuchelements.cucumber.model.CucumberFeature;
import com.nosuchelements.pdf.ColorScheme;
import com.nosuchelements.pdf.PdfStyler;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import java.io.IOException;
import java.util.List;

/**
 * Features section — one row per feature with stats and qTest/case-ID tag.
 *
 * <h3>Fixes applied</h3>
 * <ul>
 *   <li><b>F4</b> – URI normalisation now correctly strips Windows-style
 *       {@code file:///C:/} prefixes via {@link FeatureUtils#normaliseUri(String)}.</li>
 *   <li><b>F5</b> – Footer draw order corrected: {@code hLine} drawn, then cursor
 *       advances, then summary text drawn at the new position (no overlap).</li>
 *   <li><b>D3</b> – {@code extractCaseId} delegated to {@link FeatureUtils}.</li>
 *   <li><b>D4</b> – Footer uses {@link PluginVersion#FULL}.</li>
 * </ul>
 */
public class FeaturesSection {

    private static final float M       = ConsolidatedPageCursor.MARGIN_H;
    private static final float CW      = ConsolidatedPageCursor.CONTENT_W;
    private static final float ROW     = 22f;
    private static final float HDR_ROW = 20f;

    private static final float C_IDX   = M + 6f;
    private static final float C_NAME  = M + 24f;
    private static final float C_CASE  = M + CW * 0.40f;
    private static final float C_ST    = M + CW * 0.54f;
    private static final float C_SCEN  = M + CW * 0.63f;
    private static final float C_BAR   = M + CW * 0.73f;
    private static final float C_TIME  = M + CW * 0.88f;

    private final PdfStyler s;
    private final String    tagPrefix;

    public FeaturesSection(PdfStyler styler, String tagPrefix) {
        this.s         = styler;
        this.tagPrefix = tagPrefix;
    }

    public void build(PDDocument doc, PDPage firstPage,
                      List<CucumberFeature> features,
                      TableOfContents toc) throws IOException {

        ConsolidatedPageCursor cur = new ConsolidatedPageCursor(
                doc, firstPage, s, "Features");
        toc.add("Features", cur.currentPageIndex());

        SectionHeader.draw(cur, s, "Features",
                features.size() + " feature" + (features.size() == 1 ? "" : "s"),
                ColorScheme.ACCENT);

        drawColumnHeader(cur);

        boolean alt = false;
        int idx = 1;
        for (CucumberFeature f : features) {
            cur.ensureSpace(ROW + 2f);
            drawFeatureRow(cur, f, idx++, alt);
            alt = !alt;
        }

        drawSectionFooter(cur, features);
    }

    private void drawColumnHeader(ConsolidatedPageCursor cur) throws IOException {
        cur.ensureSpace(HDR_ROW + 2f);
        try (PDPageContentStream cs = cs(cur)) {
            s.fillRect(cs, M, cur.y - HDR_ROW, CW, HDR_ROW, ColorScheme.HEADER);
            float hy = cur.y - HDR_ROW + 5f;
            s.drawText(cur.doc, cs, "#",         C_IDX,  hy, s.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Feature",   C_NAME, hy, s.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Case ID",   C_CASE, hy, s.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Status",    C_ST,   hy, s.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Scenarios", C_SCEN, hy, s.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Progress",  C_BAR,  hy, s.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Duration",  C_TIME, hy, s.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
        }
        cur.advance(HDR_ROW + 1f);
    }

    private void drawFeatureRow(ConsolidatedPageCursor cur,
                                 CucumberFeature f,
                                 int idx, boolean alt) throws IOException {
        String st     = f.getOverallStatus();
        // D3 fix: use shared FeatureUtils
        String caseId = FeatureUtils.extractCaseId(f, tagPrefix);
        long   dur    = 0;
        for (var sc : f.getActualScenarios()) dur += sc.getDurationMillis();

        try (PDPageContentStream cs = cs(cur)) {
            float bgY = cur.y - ROW;
            s.fillRect(cs, M, bgY, CW, ROW,
                    alt ? ColorScheme.ROW_ALT : ColorScheme.CARD_BG);
            s.fillRect(cs, M, bgY, 3f, ROW, ColorScheme.forStatus(st));

            float ry = cur.y - ROW + 7f;

            s.drawText(cur.doc, cs, str(idx), C_IDX, ry,
                    s.regularFont(), 8f, ColorScheme.TEXT_MUTED);

            String nm = trunc(safe(f.getName()), 30);
            // F4 fix: normalise URI via FeatureUtils
            String uri = trunc(FeatureUtils.normaliseUri(f.getUri()), 34);

            s.drawText(cur.doc, cs, nm, C_NAME, ry + 4f,
                    s.boldFont(), 9f, ColorScheme.TEXT_SECONDARY);
            if (!uri.isEmpty()) {
                s.drawText(cur.doc, cs, uri, C_NAME, ry - 5f,
                        s.regularFont(), 6.5f, ColorScheme.TEXT_HINT);
            }

            s.drawText(cur.doc, cs, caseId, C_CASE, ry,
                    s.regularFont(), 8f,
                    "NA".equals(caseId) ? ColorScheme.TEXT_HINT : ColorScheme.ACCENT);

            s.drawText(cur.doc, cs, st, C_ST, ry,
                    s.boldFont(), 8f, ColorScheme.textForStatus(st));

            s.drawText(cur.doc, cs,
                    f.getPassedScenarios() + " / " + f.getTotalScenarios(),
                    C_SCEN, ry, s.regularFont(), 8f, ColorScheme.TEXT_SECONDARY);

            float barW = CW * 0.13f;
            s.drawProgressBar(cs, C_BAR, ry, barW, 6f,
                    f.getPassedSteps(), f.getFailedSteps(), f.getSkippedSteps());

            s.drawText(cur.doc, cs, fmtMs(dur), C_TIME, ry,
                    s.regularFont(), 8f, ColorScheme.TEXT_MUTED);

            s.hLine(cs, M, M + CW, bgY, ColorScheme.BORDER_SUBTLE, 0.3f);
        }
        cur.advance(ROW);
    }

    /**
     * F5 fix: draw hLine, advance cursor, THEN draw the summary text so the
     * text is positioned correctly below the line (no overlap).
     */
    private void drawSectionFooter(ConsolidatedPageCursor cur,
                                    List<CucumberFeature> features) throws IOException {
        int tf = features.size();
        int pf = (int) features.stream()
                .filter(f -> "PASSED".equalsIgnoreCase(f.getOverallStatus())).count();
        int ff = (int) features.stream()
                .filter(f -> "FAILED".equalsIgnoreCase(f.getOverallStatus())).count();

        cur.ensureSpace(28f);
        // Draw the horizontal rule at the current cursor y
        try (PDPageContentStream cs = cs(cur)) {
            s.hLine(cs, M, M + CW, cur.y, ColorScheme.BORDER, 0.5f);
        }
        // Advance AFTER the line so the summary text lands below it
        cur.advance(12f);
        try (PDPageContentStream cs = cs(cur)) {
            String summary = "Total: " + tf + " features  —  "
                    + pf + " passed   " + ff + " failed   " + (tf - pf - ff) + " skipped";
            s.drawText(cur.doc, cs, summary,
                    M, cur.y,
                    s.boldFont(), 8.5f, ColorScheme.TEXT_SECONDARY);
            // D4: version in section footer
            s.drawText(cur.doc, cs, PluginVersion.FULL + "  |  Features",
                    M, 14f, s.regularFont(), 7f, ColorScheme.TEXT_HINT);
        }
        cur.advance(18f);
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
}
