package com.nosuchelements.consolidated.sections;

import com.nosuchelements.consolidated.ContentBlockRenderer;
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
import java.util.List;

/**
 * Expanded section -- screenshots and full attachments, one group per scenario.
 *
 * <p>Only included when {@code displayExpanded=true} in the Mojo configuration.
 * Delegates all image rendering to {@link ContentBlockRenderer}.</p>
 *
 * <h3>Layout per scenario that has screenshots</h3>
 * <pre>
 * +--------------------------------------------------------------+
 * |  Feature Name  (breadcrumb)                [PASSED badge]   |
 * |  Scenario Name                                              |
 * +--------------------------------------------------------------+
 *   Screenshots
 *   +------------------------------------------------------------+
 *   |  [image 1 -- full width, up to 490x310pt]                  |
 *   +------------------------------------------------------------+
 *   +------------------------------------------------------------+
 *   |  [image 2]                                                 |
 *   +------------------------------------------------------------+
 * </pre>
 *
 * <p>Scenarios with no screenshots are silently skipped. If the entire
 * run has no screenshots a placeholder message is shown.</p>
 */
public class ExpandedSection {

    private static final float M    = ConsolidatedPageCursor.MARGIN_H;
    private static final float CW   = ConsolidatedPageCursor.CONTENT_W;
    private static final float SHDR = 30f;

    private final PdfStyler            styler;
    private final ContentBlockRenderer renderer;

    public ExpandedSection(PdfStyler styler, int maxOutputLines) {
        this.styler   = styler;
        this.renderer = new ContentBlockRenderer(styler, maxOutputLines);
    }

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public void build(PDDocument doc, PDPage firstPage,
                      List<CucumberFeature> features,
                      TableOfContents toc) throws IOException {

        ConsolidatedPageCursor cur = new ConsolidatedPageCursor(
                doc, firstPage, styler, "Expanded");

        toc.add("Expanded", cur.currentPageIndex());

        // ASCII-only title: replaced em-dash with " - "
        SectionHeader.draw(cur, styler,
                "Expanded - Screenshots & Attachments", null,
                ColorScheme.PENDING);

        boolean anyScreenshots = false;

        for (CucumberFeature feature : features) {
            for (CucumberScenario sc : feature.getActualScenarios()) {
                List<String> shots = sc.getAllScreenshots();
                if (shots.isEmpty()) continue;

                anyScreenshots = true;
                cur.ensureSpace(SHDR + 60f);   // header + at least one thumbnail
                drawScenarioHeader(cur, feature, sc);
                renderer.renderScreenshotGroup(cur, shots, true);
                cur.advance(12f);
            }
        }

        if (!anyScreenshots) {
            cur.ensureSpace(40f);
            try (PDPageContentStream cs = cs(cur)) {
                styler.drawText(cur.doc, cs,
                        "No screenshots were captured in this test run.",
                        M, cur.y, styler.regularFont(), 11f, ColorScheme.TEXT_MUTED);
            }
            cur.advance(20f);
        }

        drawSectionFooter(cur);
    }

    // -----------------------------------------------------------------------
    // Scenario sub-header
    // -----------------------------------------------------------------------

    private void drawScenarioHeader(ConsolidatedPageCursor cur,
                                     CucumberFeature feature,
                                     CucumberScenario sc) throws IOException {
        String st   = sc.getStatus();
        String feat = trunc(safe(feature.getName()), 60);
        String name = trunc(safe(sc.getName()), 70);

        try (PDPageContentStream cs = cs(cur)) {
            // Card background with left status stripe
            styler.fillRect(cs, M, cur.y - SHDR, CW, SHDR, ColorScheme.bgForStatus(st));
            styler.fillRect(cs, M, cur.y - SHDR, 3.5f, SHDR, ColorScheme.forStatus(st));
            styler.strokeRect(cs, M, cur.y - SHDR, CW, SHDR, ColorScheme.BORDER, 0.4f);

            float fy = cur.y - SHDR + 8f;
            // Feature breadcrumb
            styler.drawText(cur.doc, cs, feat,
                    M + 12f, fy + 11f,
                    styler.regularFont(), 7f, ColorScheme.TEXT_MUTED);
            // Scenario name
            styler.drawText(cur.doc, cs, name,
                    M + 12f, fy,
                    styler.boldFont(), 10f, ColorScheme.TEXT_PRIMARY);

            // Status badge (right)
            float bW = 66f, bH = 16f;
            float bX = M + CW - bW;
            styler.fillRect(cs, bX, fy, bW, bH, ColorScheme.forStatus(st));
            styler.drawText(cur.doc, cs, st,
                    bX + 7f, fy + 4f,
                    styler.boldFont(), 8f, ColorScheme.TEXT_WHITE);
        }
        cur.advance(SHDR + 6f);
    }

    // -----------------------------------------------------------------------
    // Footer
    // -----------------------------------------------------------------------

    private void drawSectionFooter(ConsolidatedPageCursor cur) throws IOException {
        try (PDPageContentStream cs = new PDPageContentStream(
                cur.doc, cur.page, PDPageContentStream.AppendMode.APPEND, true)) {
            float W = ConsolidatedPageCursor.PAGE_W;
            styler.hLine(cs, M, W - M, 28f, ColorScheme.BORDER, 0.4f);
            styler.drawText(cur.doc, cs,
                    "Cucumber PDF Reporter v1.5.0  |  Expanded",
                    M, 14f, styler.regularFont(), 7f, ColorScheme.TEXT_HINT);
        }
    }

    // -----------------------------------------------------------------------
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
