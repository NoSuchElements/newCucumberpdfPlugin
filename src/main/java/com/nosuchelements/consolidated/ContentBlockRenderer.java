package com.nosuchelements.consolidated;

import com.nosuchelements.cucumber.model.CucumberTableRow;
import com.nosuchelements.pdf.ColorScheme;
import com.nosuchelements.pdf.PdfStyler;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

/**
 * Shared content-block rendering primitives used by both
 * {@link com.nosuchelements.consolidated.sections.DetailedSection} and
 * {@link com.nosuchelements.consolidated.sections.ExpandedSection}.
 *
 * <p>Eliminates the duplication that existed when both sections independently
 * rendered error blocks, logs, data tables, DocStrings, and screenshots.</p>
 *
 * <h3>Design notes</h3>
 * <ul>
 *   <li>All methods take a {@link ConsolidatedPageCursor} — they handle their own
 *       page-overflow via {@code cur.ensureSpace()}.</li>
 *   <li>Every method returns {@code void}; the cursor position is updated in-place.</li>
 *   <li>Stateless — safe to share across parallel calls if needed.</li>
 * </ul>
 *
 * <h3>Spacing constants (v1.4.1)</h3>
 * <ul>
 *   <li>{@link #BLOCK_GAP_BEFORE} — vertical gap inserted <em>before</em> every
 *       error, log, data-table, and DocString block so they breathe away from the
 *       step line above them.</li>
 *   <li>{@link #SCREENSHOT_GAP_BEFORE} — slightly larger gap before the first
 *       screenshot in a group (or before the "Screenshots" label when shown).</li>
 *   <li>{@link #INTER_SCREENSHOT_GAP} — gap between consecutive screenshots in a
 *       group, replacing the old hard-coded {@code 6f}.</li>
 * </ul>
 */
public class ContentBlockRenderer {

    private static final Logger log = LoggerFactory.getLogger(ContentBlockRenderer.class);

    private static final float M   = ConsolidatedPageCursor.MARGIN_H;
    private static final float CW  = ConsolidatedPageCursor.CONTENT_W;
    private static final float LSM = 11f;   // small line spacing
    private static final float LMD = 14f;   // medium line spacing

    // ------------------------------------------------------------------
    // Spacing constants — v1.4.1
    // ------------------------------------------------------------------
    /** Vertical gap inserted BEFORE every error / log / table / DocString block. */
    private static final float BLOCK_GAP_BEFORE      = 5f;
    /** Vertical gap inserted BEFORE the first screenshot (or its label). */
    private static final float SCREENSHOT_GAP_BEFORE = 8f;
    /** Vertical gap between consecutive screenshots in a group. */
    private static final float INTER_SCREENSHOT_GAP  = 10f;

    // Maximum screenshot dimensions (points)
    private static final float IMG_W = 490f;
    private static final float IMG_H = 310f;

    private final PdfStyler styler;
    private final int       maxOutputLines;

    public ContentBlockRenderer(PdfStyler styler, int maxOutputLines) {
        this.styler         = styler;
        this.maxOutputLines = Math.max(1, maxOutputLines);
    }

    // =========================================================================
    // Error block  (red tint + left accent stripe)
    // =========================================================================

    /**
     * Render a multi-line error / stack-trace block.
     *
     * <p>A {@link #BLOCK_GAP_BEFORE} gap is inserted before the block so it
     * breathes away from the step line or label drawn above it.</p>
     *
     * @param cur      page cursor
     * @param error    raw error string (may contain CRLF, tabs, Unicode)
     * @param maxLines hard cap on displayed lines (pass {@code -1} to use
     *                 {@link #maxOutputLines})
     */
    public void renderErrorBlock(ConsolidatedPageCursor cur,
                                 String error,
                                 int maxLines) throws IOException {
        if (error == null || error.isEmpty()) return;
        int effectiveMax = (maxLines < 0) ? maxOutputLines : maxLines;
        String[] lines = error.split("\\r?\\n");
        int shown = Math.min(lines.length, effectiveMax);
        float bH  = shown * LSM + (lines.length > effectiveMax ? LSM : 0) + 12f;

        // SP-1: breathing room before the block
        cur.advance(BLOCK_GAP_BEFORE);
        cur.ensureSpace(bH + 6f);

        float bX = M + 10f, bW = CW - 10f, bY = cur.y - bH;
        try (PDPageContentStream cs = cs(cur)) {
            styler.fillRect(cs, bX, bY, bW, bH, ColorScheme.FAILED_BG);
            styler.fillRect(cs, bX, bY, 2.5f, bH, ColorScheme.FAILED);
            float ty = cur.y - 8f;
            for (int i = 0; i < shown; i++) {
                styler.drawText(cur.doc, cs,
                        trunc(sanitiseLogLine(lines[i]), 110),
                        bX + 10f, ty, styler.monoFont(), 7.5f, ColorScheme.FAILED_TEXT);
                ty -= LSM;
            }
            if (lines.length > effectiveMax) {
                styler.drawText(cur.doc, cs,
                        "... +" + (lines.length - effectiveMax) + " more lines",
                        bX + 10f, ty, styler.monoFont(), 7.5f, ColorScheme.TEXT_HINT);
            }
        }
        cur.advance(bH + 6f);
    }

    // =========================================================================
    // Output log block  (slate tint + grey left stripe)
    // =========================================================================

    /**
     * Render step output lines, optionally preceded by a section label.
     *
     * <p>A {@link #BLOCK_GAP_BEFORE} gap is inserted before this block.</p>
     *
     * @param cur   page cursor
     * @param lines log lines to display (truncated to {@link #maxOutputLines})
     * @param label section label (e.g. "Logs", "Hook output") — may be {@code null}
     */
    public void renderLogs(ConsolidatedPageCursor cur,
                           List<String> lines,
                           String label) throws IOException {
        if (lines == null || lines.isEmpty()) return;

        int shown = Math.min(lines.size(), maxOutputLines);
        float bH  = shown * LSM + (lines.size() > maxOutputLines ? LSM : 0) + 10f;
        float reserve = (label != null ? LMD + 4f : 0) + bH + 6f;

        // SP-1: breathing room before the block
        cur.advance(BLOCK_GAP_BEFORE);
        cur.ensureSpace(reserve);

        if (label != null) {
            try (PDPageContentStream cs = cs(cur)) {
                styler.drawText(cur.doc, cs, label, M, cur.y,
                        styler.boldFont(), 8.5f, ColorScheme.TEXT_MUTED);
            }
            cur.advance(LMD);
        }

        float bX = M + 10f, bW = CW - 10f, bY = cur.y - bH;
        try (PDPageContentStream cs = cs(cur)) {
            styler.fillRect(cs, bX, bY, bW, bH, ColorScheme.ROW_ALT);
            styler.fillRect(cs, bX, bY, 2f, bH, ColorScheme.TEXT_HINT);
            float ty = cur.y - 7f;
            for (int i = 0; i < shown; i++) {
                styler.drawText(cur.doc, cs,
                        trunc(lines.get(i), 110),
                        bX + 8f, ty, styler.monoFont(), 7.5f, ColorScheme.TEXT_SECONDARY);
                ty -= LSM;
            }
            if (lines.size() > maxOutputLines) {
                styler.drawText(cur.doc, cs,
                        "... +" + (lines.size() - maxOutputLines) + " more",
                        bX + 8f, ty, styler.monoFont(), 7.5f, ColorScheme.TEXT_HINT);
            }
        }
        cur.advance(bH + 6f);
    }

    // =========================================================================
    // Data table
    // =========================================================================

    /**
     * Render a Cucumber DataTable with a dark header row.
     *
     * <p>A {@link #BLOCK_GAP_BEFORE} gap is inserted before this block.</p>
     */
    public void renderDataTable(ConsolidatedPageCursor cur,
                                List<CucumberTableRow> rows) throws IOException {
        if (rows == null || rows.isEmpty()) return;
        float tH = rows.size() * LSM + 8f;

        // SP-1: breathing room before the block
        cur.advance(BLOCK_GAP_BEFORE);
        cur.ensureSpace(tH + 6f);

        float bX = M + 10f, bW = CW - 10f, tY = cur.y - tH;
        try (PDPageContentStream cs = cs(cur)) {
            styler.fillRect(cs, bX, tY, bW, tH, ColorScheme.ROW_ALT);
            styler.strokeRect(cs, bX, tY, bW, tH, ColorScheme.BORDER, 0.5f);
            // Header row background (darker)
            styler.fillRect(cs, bX, tY + tH - LSM - 4f, bW, LSM + 4f, ColorScheme.BORDER);
            float ty = cur.y - 6f;
            for (int i = 0; i < rows.size(); i++) {
                List<String> cells = rows.get(i).getCells();
                if (cells == null || cells.isEmpty()) { ty -= LSM; continue; }
                String row = trunc("| " + String.join(" | ", cells) + " |", 110);
                styler.drawText(cur.doc, cs, row,
                        bX + 6f, ty, styler.monoFont(), 7.5f,
                        i == 0 ? ColorScheme.TEXT_PRIMARY : ColorScheme.TEXT_SECONDARY);
                ty -= LSM;
            }
        }
        cur.advance(tH + 6f);
    }

    // =========================================================================
    // DocString
    // =========================================================================

    /**
     * Render a multi-line DocString with an indigo left accent stripe.
     * Capped at 25 lines.
     *
     * <p>A {@link #BLOCK_GAP_BEFORE} gap is inserted before this block.</p>
     */
    public void renderDocString(ConsolidatedPageCursor cur,
                                String content) throws IOException {
        if (content == null || content.isEmpty()) return;
        String[] lines = content.split("\\r?\\n");
        int shown = Math.min(lines.length, 25);
        float bH  = shown * LSM + (lines.length > 25 ? LSM : 0) + 10f;

        // SP-1: breathing room before the block
        cur.advance(BLOCK_GAP_BEFORE);
        cur.ensureSpace(bH + 6f);

        float bX = M + 10f, bW = CW - 10f, bY = cur.y - bH;
        try (PDPageContentStream cs = cs(cur)) {
            styler.fillRect(cs, bX, bY, bW, bH, ColorScheme.ROW_ALT);
            styler.strokeRect(cs, bX, bY, bW, bH, ColorScheme.BORDER, 0.5f);
            styler.fillRect(cs, bX, bY, 2.5f, bH, ColorScheme.ACCENT);
            float ty = cur.y - 7f;
            for (int i = 0; i < shown; i++) {
                styler.drawText(cur.doc, cs, trunc(lines[i], 110),
                        bX + 9f, ty, styler.monoFont(), 7.5f, ColorScheme.TEXT_SECONDARY);
                ty -= LSM;
            }
            if (lines.length > 25) {
                styler.drawText(cur.doc, cs,
                        "... +" + (lines.length - 25) + " more lines",
                        bX + 9f, ty, styler.monoFont(), 7.5f, ColorScheme.TEXT_HINT);
            }
        }
        cur.advance(bH + 6f);
    }

    // =========================================================================
    // Screenshot
    // =========================================================================

    /**
     * Render all screenshots from a list, with an optional "Screenshots" section
     * label before the first image.
     *
     * <p>Changes in v1.4.1:</p>
     * <ul>
     *   <li>SP-2: {@link #SCREENSHOT_GAP_BEFORE} gap inserted before the group.</li>
     *   <li>SP-3: {@link #INTER_SCREENSHOT_GAP} replaces the old hard-coded {@code 6f}
     *       between consecutive images, giving each screenshot room to breathe.</li>
     * </ul>
     *
     * @param cur       page cursor
     * @param shots     base64 PNG/JPEG strings
     * @param showLabel if {@code true}, draw a "Screenshots" label before the first image
     */
    public void renderScreenshotGroup(ConsolidatedPageCursor cur,
                                      List<String> shots,
                                      boolean showLabel) throws IOException {
        if (shots == null || shots.isEmpty()) return;

        // SP-2: breathing room before the screenshot group
        cur.advance(SCREENSHOT_GAP_BEFORE);

        for (int i = 0; i < shots.size(); i++) {
            if (i == 0 && showLabel) {
                cur.ensureSpace(LMD + 60f);
                try (PDPageContentStream cs = cs(cur)) {
                    styler.drawText(cur.doc, cs, "Screenshots",
                            M, cur.y, styler.boldFont(), 9f, ColorScheme.ACCENT);
                }
                cur.advance(LMD);
            }
            renderSingleScreenshot(cur, shots.get(i));
            // SP-3: inter-screenshot gap (replaces hard-coded 6f)
            if (i < shots.size() - 1) {
                cur.advance(INTER_SCREENSHOT_GAP);
            }
        }
    }

    /**
     * Render a single screenshot with a white card frame.
     *
     * <p>SP-4 (v1.4.1): bottom card padding increased from {@code 12f} to
     * {@code 16f} so the card visually closes before the next element.</p>
     */
    public void renderSingleScreenshot(ConsolidatedPageCursor cur,
                                       String b64) throws IOException {
        if (b64 == null || b64.isEmpty()) return;
        try {
            byte[]        bytes = Base64.getDecoder().decode(b64.trim());
            PDImageXObject img  = PDImageXObject.createFromByteArray(cur.doc, bytes, "screenshot");
            float scale = Math.min(
                    Math.min(IMG_W / img.getWidth(), IMG_H / img.getHeight()), 1f);
            float dW = img.getWidth()  * scale;
            float dH = img.getHeight() * scale;

            cur.ensureSpace(dH + 20f);

            float fX = M + 4f, fW = CW - 4f, fY = cur.y - dH - 8f;
            try (PDPageContentStream cs = cs(cur)) {
                styler.drawCard(cs, fX, fY - 4f, fW, dH + 8f);
                cs.drawImage(img, fX + (fW - dW) / 2f, fY, dW, dH);
            }
            // SP-4: increased from 12f → 16f for card breathing room
            cur.advance(dH + 16f);

        } catch (Exception e) {
            log.warn("Screenshot decode failed ({}): {}", b64.length(), e.getMessage());
            cur.ensureSpace(LMD + 4f);
            try (PDPageContentStream cs = cs(cur)) {
                styler.drawText(cur.doc, cs,
                        "[Screenshot decode failed: " + e.getMessage() + "]",
                        M + 10f, cur.y, styler.monoFont(), 8f, ColorScheme.FAILED_TEXT);
            }
            cur.advance(LMD + 4f);
        }
    }

    // =========================================================================
    // Step keyword helpers
    // =========================================================================

    /**
     * Returns {@code true} if {@code kw} is a continuation keyword (And / But).
     * Case-insensitive, trims trailing spaces.
     */
    public static boolean isContinuationKeyword(String kw) {
        if (kw == null || kw.trim().isEmpty()) return false;
        String t = kw.trim().toLowerCase();
        return t.equals("and") || t.equals("but");
    }

    /**
     * Calculate how many characters fit on a single step-name line given the
     * keyword length and whether the step is a continuation.
     */
    public static int availStepChars(int kwLen, boolean isContinuation) {
        float pageW  = ConsolidatedPageCursor.PAGE_W;
        float margin = ConsolidatedPageCursor.MARGIN_H;
        float durW   = 44f;
        float charW  = 5.4f;
        float nameX  = isContinuation
                ? margin + 22f + kwLen * 5.1f
                : margin + 14f + kwLen * 5.5f;
        return Math.max(20, (int) ((pageW - margin - durW - nameX) / charW));
    }

    /**
     * Wrap a step name into at most two display lines.
     */
    public static String[] wrapStepName(String name, int maxChars) {
        if (name == null || name.isEmpty()) return new String[]{""};
        if (name.length() <= maxChars)      return new String[]{name};
        String sub = name.substring(0, maxChars);
        int sp = sub.lastIndexOf(' ');
        if (sp > 0) {
            String rest = name.substring(sp + 1);
            String line2 = rest.length() > maxChars
                    ? rest.substring(0, maxChars - 3) + "..." : rest;
            return new String[]{ name.substring(0, sp), line2 };
        }
        String line2 = name.length() > maxChars * 2
                ? name.substring(maxChars, maxChars * 2 - 3) + "..."
                : name.substring(maxChars);
        return new String[]{ name.substring(0, maxChars), line2 };
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private PDPageContentStream cs(ConsolidatedPageCursor cur) throws IOException {
        return new PDPageContentStream(cur.doc, cur.page,
                PDPageContentStream.AppendMode.APPEND, true);
    }

    private static String sanitiseLogLine(String s) {
        return s == null ? "" : s.replace("\t", "    ");
    }

    private static String trunc(String v, int n) {
        if (v == null) return "";
        return v.length() > n ? v.substring(0, n - 3) + "..." : v;
    }
}
