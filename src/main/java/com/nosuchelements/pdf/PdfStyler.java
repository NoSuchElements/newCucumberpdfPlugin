package com.nosuchelements.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;

import java.awt.Color;
import java.io.IOException;

/**
 * Central drawing-primitives facade over PDFBox.
 *
 * <p>Every section in the consolidated report calls methods on this class
 * rather than calling PDFBox APIs directly.  This keeps the coordinate logic
 * inside the sections and the raw PDFBox calls here, where they can be
 * updated in one place.</p>
 *
 * <p>All coordinates follow the PDFBox convention: origin bottom-left,
 * Y increases upward.</p>
 */
public class PdfStyler {

    // -----------------------------------------------------------------------
    // Font accessors
    // -----------------------------------------------------------------------

    public PDFont regularFont() { return PDType1Font.HELVETICA; }
    public PDFont boldFont()    { return PDType1Font.HELVETICA_BOLD; }
    public PDFont italicFont()  { return PDType1Font.HELVETICA_OBLIQUE; }
    public PDFont monoFont()    { return PDType1Font.COURIER; }

    // -----------------------------------------------------------------------
    // Text
    // -----------------------------------------------------------------------

    /**
     * Draw a single-line text string at (x, y).
     *
     * @param doc    the document (needed for font embedding in some configurations)
     * @param cs     the open content stream
     * @param text   text to draw (null/empty is a no-op)
     * @param x      left x position
     * @param y      baseline y position
     * @param font   PDFont to use
     * @param size   font size in points
     * @param color  fill colour
     */
    public void drawText(PDDocument doc,
                         PDPageContentStream cs,
                         String text,
                         float x, float y,
                         PDFont font, float size,
                         Color color) throws IOException {
        if (text == null || text.isEmpty()) return;
        String safe = sanitise(text);
        if (safe.isEmpty()) return;
        cs.setNonStrokingColor(toAWT(color));
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(safe);
        cs.endText();
    }

    // -----------------------------------------------------------------------
    // Filled rectangles
    // -----------------------------------------------------------------------

    /**
     * Fill an axis-aligned rectangle.
     *
     * @param cs    content stream
     * @param x     left
     * @param y     bottom
     * @param w     width
     * @param h     height
     * @param color fill color
     */
    public void fillRect(PDPageContentStream cs,
                         float x, float y, float w, float h,
                         Color color) throws IOException {
        if (w <= 0 || h <= 0) return;
        cs.setNonStrokingColor(toAWT(color));
        cs.addRect(x, y, w, h);
        cs.fill();
    }

    // -----------------------------------------------------------------------
    // Stroked rectangles
    // -----------------------------------------------------------------------

    /**
     * Draw a stroked (outline) rectangle.
     */
    public void strokeRect(PDPageContentStream cs,
                           float x, float y, float w, float h,
                           Color color, float lineWidth) throws IOException {
        if (w <= 0 || h <= 0) return;
        cs.setStrokingColor(toAWT(color));
        cs.setLineWidth(lineWidth);
        cs.addRect(x, y, w, h);
        cs.stroke();
    }

    // -----------------------------------------------------------------------
    // Card (white rounded outline box — simulated with filled + stroked rect)
    // -----------------------------------------------------------------------

    /**
     * Draw a white card with a thin grey border.
     */
    public void drawCard(PDPageContentStream cs,
                         float x, float y, float w, float h) throws IOException {
        if (w <= 0 || h <= 0) return;
        fillRect(cs, x, y, w, h, Color.WHITE);
        strokeRect(cs, x, y, w, h, ColorScheme.BORDER, 0.5f);
    }

    // -----------------------------------------------------------------------
    // Horizontal line
    // -----------------------------------------------------------------------

    /**
     * Draw a horizontal rule from x1 to x2 at height y.
     */
    public void hLine(PDPageContentStream cs,
                      float x1, float x2, float y,
                      Color color, float lineWidth) throws IOException {
        cs.setStrokingColor(toAWT(color));
        cs.setLineWidth(lineWidth);
        cs.moveTo(x1, y);
        cs.lineTo(x2, y);
        cs.stroke();
    }

    // -----------------------------------------------------------------------
    // Dot (filled circle)
    // -----------------------------------------------------------------------

    /**
     * Draw a filled circle (dot) at (cx, cy) with the given radius.
     * PDFBox does not have a built-in circle; we approximate with a Bézier path.
     */
    public void dot(PDPageContentStream cs,
                    float cx, float cy, float r,
                    Color color) throws IOException {
        cs.setNonStrokingColor(toAWT(color));
        float k = 0.5523f * r;
        cs.moveTo(cx, cy + r);
        cs.curveTo(cx + k, cy + r, cx + r, cy + k, cx + r, cy);
        cs.curveTo(cx + r, cy - k, cx + k, cy - r, cx, cy - r);
        cs.curveTo(cx - k, cy - r, cx - r, cy - k, cx - r, cy);
        cs.curveTo(cx - r, cy + k, cx - k, cy + r, cx, cy + r);
        cs.fill();
    }

    // -----------------------------------------------------------------------
    // Progress bar  (three-segment pass/fail/skip)
    // -----------------------------------------------------------------------

    /**
     * Draw a horizontal segmented progress bar showing pass/fail/skip proportions.
     *
     * @param cs      content stream
     * @param x       left x
     * @param y       bottom y
     * @param w       total width
     * @param h       height
     * @param passed  count of passed steps/scenarios
     * @param failed  count of failed
     * @param skipped count of skipped
     */
    public void drawProgressBar(PDPageContentStream cs,
                                float x, float y, float w, float h,
                                int passed, int failed, int skipped) throws IOException {
        int total = passed + failed + skipped;
        if (total <= 0) {
            fillRect(cs, x, y, w, h, ColorScheme.BORDER);
            return;
        }
        float pW = w * passed  / total;
        float fW = w * failed  / total;
        float sW = w - pW - fW;

        if (pW > 0) fillRect(cs, x,        y, pW, h, ColorScheme.PASSED);
        if (fW > 0) fillRect(cs, x + pW,   y, fW, h, ColorScheme.FAILED);
        if (sW > 0) fillRect(cs, x + pW + fW, y, sW, h, ColorScheme.SKIPPED);
        strokeRect(cs, x, y, w, h, ColorScheme.BORDER, 0.3f);
    }

    // -----------------------------------------------------------------------
    // Colour conversion
    // -----------------------------------------------------------------------

    private static java.awt.Color toAWT(Color c) {
        return c != null ? c : Color.BLACK;
    }

    // -----------------------------------------------------------------------
    // Text safety
    // -----------------------------------------------------------------------

    /**
     * Remove characters that PDFBox's built-in Helvetica/Courier fonts cannot encode
     * (anything outside Windows-1252). Replaces them with '?'.
     */
    private static String sanitise(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c < 32 && c != '\t') continue;   // skip control chars
            // PDType1Font uses WinAnsiEncoding (code points 32-255)
            if (c > 255) sb.append('?');
            else         sb.append(c);
        }
        return sb.toString();
    }
}
