package com.nosuchelements.consolidated;

import com.nosuchelements.pdf.ColorScheme;
import com.nosuchelements.pdf.PdfStyler;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Stateful vertical cursor for consolidated PDF sections.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Track the current Y position as content is appended top-to-bottom.</li>
 *   <li>Detect when a new page is needed and append it automatically.</li>
 *   <li>Draw a slim continuation banner at the top of overflow pages.</li>
 *   <li>Collect the start-page index of every section for TOC / page-number stamps.</li>
 * </ul>
 *
 * <h3>Page numbering</h3>
 * Because PDFBox does not support total-page-count references during streaming,
 * page numbers are stamped in a second pass by
 * {@link ConsolidatedPdfGenerator#stampPageNumbers(PDDocument, List)}.
 * Each cursor registers itself with the generator's shared
 * {@link PageNumberRegistry} so the generator can iterate all pages after
 * all sections have been written.
 */
public class ConsolidatedPageCursor {

    // ---- Layout constants (public so sections can reference them) ----------
    public static final float MARGIN_H  = 36f;
    public static final float MARGIN_V  = 36f;
    public static final float PAGE_W    = PDRectangle.A4.getWidth();   // 595.28
    public static final float PAGE_H    = PDRectangle.A4.getHeight();  // 841.89
    public static final float CONTENT_W = PAGE_W - 2 * MARGIN_H;
    public static final float BOT       = 44f;   // bottom dead-zone (footer lives here)
    public static final float CONT_H    = 18f;   // continuation banner height

    // ---- State -------------------------------------------------------------
    public final  PDDocument doc;
    public        PDPage     page;
    public        float      y;

    private final PdfStyler  styler;
    private final String     sectionName;

    /** Tracks the 0-based page index where this cursor started each new page. */
    private final PageNumberRegistry pageRegistry;

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    /**
     * Create a cursor starting on {@code firstPage}.
     *
     * @param doc          the open PDDocument
     * @param firstPage    first page (already added to doc)
     * @param styler       drawing primitives
     * @param sectionName  shown in continuation banners
     */
    public ConsolidatedPageCursor(PDDocument doc, PDPage firstPage,
                                   PdfStyler styler, String sectionName) {
        this(doc, firstPage, styler, sectionName, null);
    }

    /**
     * Create a cursor with page-number tracking.
     *
     * @param pageRegistry shared registry; pass {@code null} to opt out of numbering
     */
    public ConsolidatedPageCursor(PDDocument doc, PDPage firstPage,
                                   PdfStyler styler, String sectionName,
                                   PageNumberRegistry pageRegistry) {
        this.doc          = doc;
        this.page         = firstPage;
        this.styler       = styler;
        this.sectionName  = sectionName != null ? sectionName : "";
        this.pageRegistry = pageRegistry;
        this.y            = PAGE_H - MARGIN_V;

        if (pageRegistry != null) {
            pageRegistry.register(doc.getNumberOfPages() - 1);
        }
    }

    // -----------------------------------------------------------------------
    // Movement
    // -----------------------------------------------------------------------

    /** Move cursor down by {@code d} points. */
    public void advance(float d) { y -= d; }

    /**
     * Ensure at least {@code needed} points remain below the cursor.
     * Appends a new A4 page if insufficient space.
     */
    public void ensureSpace(float needed) throws IOException {
        if (y - needed < BOT) {
            newPage();
        }
    }

    /**
     * Force a new page immediately (e.g. to start a section cleanly).
     */
    public void newPage() throws IOException {
        page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        y = PAGE_H - MARGIN_V;
        if (pageRegistry != null) {
            pageRegistry.register(doc.getNumberOfPages() - 1);
        }
        drawContinuationBanner();
    }

    /** Current 1-based page number in the document. */
    public int currentPageIndex() {
        return doc.getNumberOfPages();
    }

    // -----------------------------------------------------------------------
    // Continuation banner  (drawn at top of every overflow page)
    // -----------------------------------------------------------------------

    private void drawContinuationBanner() throws IOException {
        if (sectionName.isEmpty()) return;
        String label = sectionName + "  (continued)";
        try (PDPageContentStream cs = openStream()) {
            styler.fillRect(cs, 0, y - CONT_H, PAGE_W, CONT_H, ColorScheme.ROW_ALT);
            styler.hLine(cs, 0, PAGE_W, y - CONT_H, ColorScheme.BORDER, 0.4f);
            styler.drawText(doc, cs, label,
                    MARGIN_H, y - CONT_H + 5f,
                    styler.regularFont(), 8f, ColorScheme.TEXT_HINT);
        }
        y -= (CONT_H + 6f);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private PDPageContentStream openStream() throws IOException {
        return new PDPageContentStream(doc, page,
                PDPageContentStream.AppendMode.APPEND, true);
    }

    // -----------------------------------------------------------------------
    // Page number registry (inner class — keeps everything self-contained)
    // -----------------------------------------------------------------------

    /**
     * Collects 0-based page indices across all cursors in a single consolidated report.
     * After all sections are written, pass this to
     * {@link ConsolidatedPdfGenerator#stampPageNumbers} for a second-pass footer stamp.
     */
    public static class PageNumberRegistry {

        private final List<Integer> pageIndices = new ArrayList<>();

        /** Register a 0-based page index. */
        public void register(int zeroBasedIndex) {
            pageIndices.add(zeroBasedIndex);
        }

        public List<Integer> getPageIndices() { return pageIndices; }

        public int getTotalPages() { return pageIndices.size(); }
    }
}
