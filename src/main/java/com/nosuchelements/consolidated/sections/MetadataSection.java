package com.nosuchelements.consolidated.sections;

import com.nosuchelements.consolidated.ConsolidatedPageCursor;
import com.nosuchelements.cucumber.model.ReportMetadata;
import com.nosuchelements.pdf.ColorScheme;
import com.nosuchelements.pdf.PdfStyler;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import java.io.IOException;
import java.util.Map;

/**
 * Renders the environment / build metadata block.
 *
 * <p>Appears inline at the bottom of the Dashboard page (not a separate section).
 * Each key-value pair is rendered as a two-column row with a subtle alternating
 * background. The block is only drawn when at least one metadata entry is present.</p>
 *
 * <pre>
 * ┌──────────────────────────────────────────────────────────────┐
 * │  Environment Metadata                                        │
 * ├──────────────────────────────────────────────────────────────┤
 * │  Environment    │  QA                                        │
 * │  Branch         │  feature/sprint-42                         │
 * │  Build          │  1234                                      │
 * │  App Version    │  2.14.0-rc3                                │
 * │  Browser        │  Chrome 124                                │
 * └──────────────────────────────────────────────────────────────┘
 * </pre>
 */
public class MetadataSection {

    private static final float M   = ConsolidatedPageCursor.MARGIN_H;
    private static final float CW  = ConsolidatedPageCursor.CONTENT_W;
    private static final float ROW = 16f;
    private static final float HDR = 18f;

    private final PdfStyler s;

    public MetadataSection(PdfStyler styler) {
        this.s = styler;
    }

    /**
     * Draw the metadata block inline at the cursor's current position.
     * Does nothing if {@code metadata} is null or empty.
     */
    public void draw(ConsolidatedPageCursor cur, ReportMetadata metadata) throws IOException {
        if (metadata == null || metadata.isEmpty()) return;

        Map<String, String> entries = metadata.getEntries();
        float totalH = HDR + entries.size() * ROW + 8f;
        cur.ensureSpace(totalH + 8f);

        // Section label
        try (PDPageContentStream cs = cs(cur)) {
            s.drawText(cur.doc, cs, "Environment Metadata",
                    M, cur.y, s.boldFont(), 9f, ColorScheme.TEXT_MUTED);
        }
        cur.advance(HDR);

        // Header row
        try (PDPageContentStream cs = cs(cur)) {
            s.fillRect(cs, M, cur.y - ROW, CW, ROW, ColorScheme.HEADER);
            float hy = cur.y - ROW + 4f;
            s.drawText(cur.doc, cs, "Key",   M + 8f,        hy, s.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
            s.drawText(cur.doc, cs, "Value", M + CW * 0.4f, hy, s.boldFont(), 7.5f, ColorScheme.TEXT_HINT);
        }
        cur.advance(ROW);

        boolean alt = false;
        for (Map.Entry<String, String> e : entries.entrySet()) {
            cur.ensureSpace(ROW + 2f);
            try (PDPageContentStream cs = cs(cur)) {
                float bgY = cur.y - ROW;
                s.fillRect(cs, M, bgY, CW, ROW,
                        alt ? ColorScheme.ROW_ALT : ColorScheme.CARD_BG);
                s.hLine(cs, M, M + CW, bgY, ColorScheme.BORDER_SUBTLE, 0.3f);
                // Vertical divider
                float divX = M + CW * 0.38f;
                s.hLine(cs, divX, divX, bgY, ColorScheme.BORDER_SUBTLE, 0.3f);

                float ry = cur.y - ROW + 4f;
                s.drawText(cur.doc, cs, trunc(e.getKey(), 30),
                        M + 8f, ry, s.boldFont(), 8.5f, ColorScheme.TEXT_SECONDARY);
                s.drawText(cur.doc, cs, trunc(e.getValue(), 60),
                        M + CW * 0.4f, ry, s.regularFont(), 8.5f, ColorScheme.ACCENT);
            }
            cur.advance(ROW);
            alt = !alt;
        }
        cur.advance(8f);
    }

    private PDPageContentStream cs(ConsolidatedPageCursor cur) throws IOException {
        return new PDPageContentStream(cur.doc, cur.page,
                PDPageContentStream.AppendMode.APPEND, true);
    }

    private static String trunc(String v, int n) {
        if (v == null) return "";
        return v.length() > n ? v.substring(0, n - 3) + "..." : v;
    }
}
