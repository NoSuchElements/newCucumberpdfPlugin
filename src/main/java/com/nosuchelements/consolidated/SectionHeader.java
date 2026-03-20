package com.nosuchelements.consolidated;

import com.nosuchelements.pdf.ColorScheme;
import com.nosuchelements.pdf.PdfStyler;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import java.io.IOException;

/**
 * Shared utility that renders the slate-900 section header band used
 * at the top of each consolidated section (Dashboard, Features, etc.).
 *
 * <pre>
 * +----------------------------------------------------+
 * |  ████  SECTION TITLE          [subtitle / badge]   |  ← 36pt band
 * +----------------------------------------------------+
 *    ^^^^ 4pt ACCENT stripe at bottom
 * </pre>
 */
public class SectionHeader {

    private static final float H   = 36f;
    private static final float PAD = ConsolidatedPageCursor.MARGIN_H;

    private SectionHeader() {}

    /**
     * Draw a section header band and return the Y cursor after it.
     *
     * @param cur        page cursor (modified: y advanced by H)
     * @param s          styler
     * @param title      main section title
     * @param subtitle   right-aligned subtitle (or null)
     * @param accentColor accent stripe color (usually ColorScheme.ACCENT)
     */
    public static void draw(ConsolidatedPageCursor cur, PdfStyler s,
                            String title, String subtitle,
                            java.awt.Color accentColor) throws IOException {
        cur.ensureSpace(H + 8f);
        float W = ConsolidatedPageCursor.PAGE_W;

        try (PDPageContentStream cs = new PDPageContentStream(
                cur.doc, cur.page, PDPageContentStream.AppendMode.APPEND, true)) {
            // Background
            s.fillRect(cs, 0, cur.y - H, W, H, ColorScheme.HEADER);
            // Bottom accent stripe
            s.fillRect(cs, 0, cur.y - H, W, 3f, accentColor);
            // Title
            s.drawText(cur.doc, cs, title,
                    PAD, cur.y - H + 12f, s.boldFont(), 14f, ColorScheme.TEXT_WHITE);
            // Optional right subtitle
            if (subtitle != null && !subtitle.isEmpty()) {
                float sx = W - PAD - subtitle.length() * 5f;
                s.drawText(cur.doc, cs, subtitle,
                        sx, cur.y - H + 12f, s.regularFont(), 9f, ColorScheme.TEXT_HINT);
            }
        }
        cur.advance(H + 8f);
    }
}
