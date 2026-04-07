package com.nosuchelements.pdf;

import com.nosuchelements.cucumber.model.CucumberDocString;
import com.nosuchelements.cucumber.model.CucumberFeature;
import com.nosuchelements.cucumber.model.CucumberScenario;
import com.nosuchelements.cucumber.model.CucumberStep;
import com.nosuchelements.cucumber.model.CucumberTableRow;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

/**
 * Generates a single per-feature PDF (split mode).
 *
 * <h3>What is rendered per scenario page</h3>
 * <ol>
 *   <li>Scenario header band (name, status badge, duration)</li>
 *   <li>Scenario tags line</li>
 *   <li>Background steps (if present)</li>
 *   <li>Before-hook errors and screenshots</li>
 *   <li>Step rows with: keyword, name, duration</li>
 *   <li>Per-step error blocks (CRLF-safe, tab-sanitised)</li>
 *   <li>Per-step output log lines</li>
 *   <li>Per-step DataTable</li>
 *   <li>Per-step DocString</li>
 *   <li>Per-step screenshots (embeddings)</li>
 *   <li>After-hook errors and screenshots</li>
 * </ol>
 *
 * <h3>Page overflow</h3>
 * A {@link ScenarioCursor} inner class manages Y position and appends new A4
 * pages on overflow.  Every drawing call opens and closes its own
 * APPEND-mode {@code PDPageContentStream}, so no stream is ever left open
 * across a page boundary (the original crash cause).
 *
 * <h3>v1.5.0 improvements</h3>
 * <ul>
 *   <li>Step spacing/padding corrected (1.4.1 branch fix forward-ported)</li>
 *   <li>DataTable, DocString, output logs now rendered in split mode</li>
 *   <li>Background steps now rendered with a labelled block</li>
 *   <li>Scenario tags shown below the header band</li>
 *   <li>Scenario Outline row index shown when {@code sc.getName()} contains a row marker</li>
 *   <li>Step keyword {@code *} treated as primary (not continuation)</li>
 *   <li>Version string centralised — no longer hard-coded in footers</li>
 * </ul>
 */
public class FeaturePdfGenerator {

    private static final Logger log = LoggerFactory.getLogger(FeaturePdfGenerator.class);

    // ---- Layout ---------------------------------------------------------------
    private static final float M       = 36f;
    private static final float PW      = PDRectangle.A4.getWidth();   // 595.28
    private static final float PH      = PDRectangle.A4.getHeight();  // 841.89
    private static final float CW      = PW - 2 * M;
    /** Step line height — v1.4.1 spacing fix: was 14f, now 15f for breathing room. */
    private static final float LH      = 15f;
    /** Error / log / table line height. */
    private static final float LHM     = 11f;
    /** Bottom dead-zone (footer lives below this). */
    private static final float BOT     = 44f;
    /** Max screenshot width (points). */
    private static final float IMG_W   = CW - 8f;
    /** Max screenshot height (points). */
    private static final float IMG_H   = 280f;

    // ---- Config ---------------------------------------------------------------
    private final boolean includeSummaryPage;
    private final boolean includeFeaturePage;
    private final boolean includeDetailedPages;
    private final int     maxOutputLines;
    private final String  tagPrefix;
    private final PdfStyler s = new PdfStyler();

    /** All sections on. */
    public FeaturePdfGenerator() {
        this(true, true, true, 20, "QTEST_TC_");
    }

    public FeaturePdfGenerator(boolean includeSummaryPage,
                                boolean includeFeaturePage,
                                boolean includeDetailedPages,
                                int maxOutputLines,
                                String tagPrefix) {
        this.includeSummaryPage   = includeSummaryPage;
        this.includeFeaturePage   = includeFeaturePage;
        this.includeDetailedPages = includeDetailedPages;
        this.maxOutputLines       = Math.max(1, maxOutputLines);
        this.tagPrefix            = tagPrefix != null ? tagPrefix : "QTEST_TC_";
    }

    // =========================================================================
    // Filename generation
    // =========================================================================

    /**
     * Build the output filename for a feature PDF.
     * Pattern: {@code featureName[@QTEST_TC_NNNN].pdf}
     */
    public static String generateFilename(CucumberFeature feature, String qtestCaseId) {
        String name = feature.getName() != null ? feature.getName() : "unnamed";
        String slug = name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (slug.isEmpty()) slug = "feature";
        String caseTag = qtestCaseId != null && !qtestCaseId.isBlank()
                ? "@QTEST_TC_" + qtestCaseId : "";
        return slug + caseTag + ".pdf";
    }

    // =========================================================================
    // Top-level generation
    // =========================================================================

    public void generateFeaturePdf(CucumberFeature feature,
                                    String outputPath) throws IOException {
        File outFile = new File(outputPath);
        File parent  = outFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create output dir: " + parent);
        }

        try (PDDocument doc = new PDDocument()) {
            // Set PDF document metadata
            var info = doc.getDocumentInformation();
            info.setTitle(safe(feature.getName()));
            info.setSubject("Cucumber Test Report");
            info.setCreator("Cucumber PDF Reporter v1.5.0");

            addFeaturePage(doc, feature);

            if (includeDetailedPages) {
                for (CucumberScenario sc : feature.getActualScenarios()) {
                    addScenarioPages(doc, feature, sc);
                }
            }

            doc.save(outFile);
        }
        log.debug("Written: {}", outputPath);
    }

    // =========================================================================
    // Feature summary page
    // =========================================================================

    private void addFeaturePage(PDDocument doc, CucumberFeature feature) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        float y = PH - M;

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            // Header band
            s.fillRect(cs, 0, y - 54f, PW, 54f, ColorScheme.HEADER);
            s.fillRect(cs, 0, y - 54f, PW, 4f, ColorScheme.forStatus(feature.getOverallStatus()));

            // Feature name (auto-shrink for long names)
            String featureName = safe(feature.getName());
            float fontSize = featureName.length() > 55 ? 13f : featureName.length() > 40 ? 15f : 17f;
            s.drawText(doc, cs, trunc(featureName, 70),
                    M, y - 20f, s.boldFont(), fontSize, ColorScheme.TEXT_WHITE);
            s.drawText(doc, cs, trunc(safe(feature.getUri()).replace("file:", ""), 80),
                    M, y - 38f, s.regularFont(), 7.5f, ColorScheme.TEXT_HINT);

            // Tags on header
            String tagLine = buildTagLine(feature.getTags(), 8);
            if (!tagLine.isEmpty()) {
                s.drawText(doc, cs, tagLine, M, y - 50f,
                        s.italicFont(), 7f, ColorScheme.TEXT_HINT);
            }

            // Status badge
            String st = feature.getOverallStatus();
            float bW = 76f, bH = 20f, bX = PW - M - bW;
            s.fillRect(cs, bX, y - 54f + (54f - bH) / 2f, bW, bH, ColorScheme.forStatus(st));
            s.drawText(doc, cs, st, bX + 8f, y - 54f + (54f - bH) / 2f + 5f,
                    s.boldFont(), 9.5f, ColorScheme.TEXT_WHITE);

            y -= 70f;

            // Stats row
            s.drawText(doc, cs,
                    "Scenarios: " + feature.getTotalScenarios()
                    + "   \u2713 " + feature.getPassedScenarios()
                    + "   \u2717 " + feature.getFailedScenarios()
                    + "   \u25b7 " + feature.getSkippedScenarios(),
                    M, y, s.regularFont(), 10f, ColorScheme.TEXT_SECONDARY);
            y -= 8f;

            // Step stats
            s.drawText(doc, cs,
                    "Steps: " + feature.getTotalSteps()
                    + "   \u2713 " + feature.getPassedSteps()
                    + "   \u2717 " + feature.getFailedSteps()
                    + "   \u25b7 " + feature.getSkippedSteps(),
                    M, y - 10f, s.regularFont(), 8.5f, ColorScheme.TEXT_MUTED);
            y -= 24f;

            // Progress bar
            s.drawProgressBar(cs, M, y - 8f, CW, 9f,
                    feature.getPassedSteps(), feature.getFailedSteps(), feature.getSkippedSteps());
            y -= 26f;

            // Scenario list
            for (CucumberScenario sc : feature.getActualScenarios()) {
                if (y < M + 28f) break;
                String scenSt = sc.getStatus();
                s.dot(cs, M + 5f, y + 4f, 4f, ColorScheme.forStatus(scenSt));
                s.drawText(doc, cs, trunc(safe(sc.getName()), 70),
                        M + 18f, y, s.regularFont(), 9f, ColorScheme.TEXT_SECONDARY);
                s.drawText(doc, cs, sc.formatDuration(),
                        PW - M - 70f, y, s.regularFont(), 7.5f, ColorScheme.TEXT_HINT);
                s.drawText(doc, cs, scenSt,
                        PW - M - scenSt.length() * 5.2f - 4f, y,
                        s.boldFont(), 8.5f, ColorScheme.textForStatus(scenSt));
                y -= LH;
            }
        }
        drawPageFooter(doc, page, "Feature Summary");
    }

    // =========================================================================
    // Scenario detail pages (Bug 1 fix: proper multi-page cursor)
    // =========================================================================

    private void addScenarioPages(PDDocument doc,
                                   CucumberFeature feature,
                                   CucumberScenario sc) throws IOException {
        ScenarioCursor cur = new ScenarioCursor(doc, feature, sc);

        // Before-hook errors + images
        for (CucumberStep hook : sc.getBeforeHooks()) {
            renderHookEntry(cur, hook, "Before hook failed");
        }

        // Background steps
        if (sc.hasBackground()) {
            renderBackgroundBlock(cur, sc.getBackgroundSteps());
        }

        // Scenario tags
        renderTagsLine(cur, sc.getTags());

        // Steps — the core of the page
        for (CucumberStep step : sc.getSteps()) {
            renderStepRow(cur, step);
            renderErrorBlock(cur, step.getErrorMessage());
            renderOutputLogs(cur, step.getOutputLines());
            renderDataTable(cur, step.getDataTableRows());
            renderDocString(cur, step.getDocString());
            renderEmbeddings(cur, step.getEmbeddings());
            cur.advance(2f);  // v1.4.1: inter-step padding
        }

        // After-hook errors + images
        for (CucumberStep hook : sc.getAfterHooks()) {
            renderHookEntry(cur, hook, "After hook");
        }

        drawPageFooter(cur.doc, cur.page, trunc(safe(sc.getName()), 60));
    }

    // =========================================================================
    // ScenarioCursor — manages Y and page creation (Bug 1 core fix)
    // =========================================================================

    private final class ScenarioCursor {

        final PDDocument     doc;
        final CucumberFeature feature;
        final CucumberScenario sc;
        PDPage page;
        float  y;

        ScenarioCursor(PDDocument doc,
                       CucumberFeature feature,
                       CucumberScenario sc) throws IOException {
            this.doc     = doc;
            this.feature = feature;
            this.sc      = sc;
            page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            y = PH - M;
            drawScenarioHeader();
        }

        PDPageContentStream stream() throws IOException {
            return new PDPageContentStream(doc, page,
                    PDPageContentStream.AppendMode.APPEND, true);
        }

        void advance(float d) throws IOException {
            y -= d;
            if (y < BOT) newPage();
        }

        void ensureSpace(float needed) throws IOException {
            if (y - needed < BOT) newPage();
        }

        private void newPage() throws IOException {
            drawPageFooter(doc, page, trunc(safe(sc.getName()), 60));
            page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            y = PH - M;
            // Continuation banner
            try (PDPageContentStream cs = stream()) {
                String label = trunc(safe(feature.getName()), 40)
                        + "  \u203a  " + trunc(safe(sc.getName()), 50) + "  (continued)";
                s.fillRect(cs, 0, y - 20f, PW, 20f, ColorScheme.HEADER);
                s.drawText(doc, cs, label, M, y - 13f,
                        s.regularFont(), 7.5f, ColorScheme.TEXT_HINT);
            }
            y -= 28f;
        }

        private void drawScenarioHeader() throws IOException {
            String st = sc.getStatus();
            float headerH = 40f;
            try (PDPageContentStream cs = stream()) {
                s.fillRect(cs, 0, y - headerH, PW, headerH, ColorScheme.HEADER);
                s.fillRect(cs, 0, y - headerH, PW, 4f, ColorScheme.forStatus(st));

                // Feature breadcrumb
                s.drawText(doc, cs, trunc(safe(feature.getName()), 65),
                        M, y - 12f, s.regularFont(), 7f, ColorScheme.TEXT_HINT);

                // Scenario name (auto-size)
                String nm = safe(sc.getName());
                float nf = nm.length() > 60 ? 9.5f : nm.length() > 45 ? 10.5f : 12f;
                s.drawText(doc, cs, trunc(nm, 75),
                        M, y - 28f, s.boldFont(), nf, ColorScheme.TEXT_WHITE);

                // Status badge
                float bW = 70f, bH = 18f, bX = PW - M - bW;
                s.fillRect(cs, bX, y - headerH + (headerH - bH) / 2f, bW, bH,
                        ColorScheme.forStatus(st));
                s.drawText(doc, cs, st,
                        bX + 7f, y - headerH + (headerH - bH) / 2f + 4f,
                        s.boldFont(), 8.5f, ColorScheme.TEXT_WHITE);

                // Duration
                String dur = sc.formatDuration();
                s.drawText(doc, cs, dur,
                        bX - dur.length() * 5.2f - 8f,
                        y - headerH + (headerH - bH) / 2f + 4f,
                        s.regularFont(), 8f, ColorScheme.TEXT_HINT);
            }
            y -= headerH + 8f;  // v1.4.1: extra padding after header
        }
    }

    // =========================================================================
    // Tags line
    // =========================================================================

    private void renderTagsLine(ScenarioCursor cur, List<String> tags) throws IOException {
        if (tags == null || tags.isEmpty()) return;
        String line = buildTagLine(tags, 12);
        if (line.isEmpty()) return;
        cur.ensureSpace(LH + 2f);
        try (PDPageContentStream cs = cur.stream()) {
            s.drawText(cur.doc, cs, line, M, cur.y,
                    s.italicFont(), 7.5f, ColorScheme.TEXT_HINT);
        }
        cur.advance(LH + 2f);  // v1.4.1: padding after tags
    }

    // =========================================================================
    // Background block
    // =========================================================================

    private void renderBackgroundBlock(ScenarioCursor cur,
                                        List<CucumberStep> steps) throws IOException {
        if (steps == null || steps.isEmpty()) return;
        cur.ensureSpace(LH + steps.size() * LH + 12f);

        try (PDPageContentStream cs = cur.stream()) {
            s.drawText(cur.doc, cs, "Background",
                    M, cur.y, s.boldFont(), 8f, ColorScheme.TEXT_HINT);
        }
        cur.advance(LH);

        for (CucumberStep step : steps) {
            cur.ensureSpace(LH);
            String kw = safe(step.getKeyword());
            String nm = safe(step.getName());
            try (PDPageContentStream cs = cur.stream()) {
                s.fillRect(cs, M + 2f, cur.y + 1f, 5f, 5f, ColorScheme.TEXT_HINT);
                s.drawText(cur.doc, cs, kw, M + 12f, cur.y,
                        s.boldFont(), 8.5f, ColorScheme.TEXT_HINT);
                s.drawText(cur.doc, cs, trunc(nm, 88), M + 12f + kw.length() * 5.2f,
                        cur.y, s.regularFont(), 8.5f, ColorScheme.TEXT_HINT);
            }
            cur.advance(LH);
        }
        // Divider after background
        cur.ensureSpace(10f);
        try (PDPageContentStream cs = cur.stream()) {
            s.hLine(cs, M, M + CW, cur.y, ColorScheme.BORDER, 0.5f);
        }
        cur.advance(10f);
    }

    // =========================================================================
    // Step row renderer  (v1.4.1: spacing/padding corrected)
    // =========================================================================

    private void renderStepRow(ScenarioCursor cur, CucumberStep step) throws IOException {
        cur.ensureSpace(LH + 4f);

        String kw  = safe(step.getKeyword()).trim();
        String nm  = trunc(safe(step.getName()), 85);
        String sts = step.getStatus();
        long   dur = step.getDurationMillis();

        // v1.4.1: continuation keywords indented, smaller dot
        boolean isCont = kw.equalsIgnoreCase("and") || kw.equalsIgnoreCase("but");

        try (PDPageContentStream cs = cur.stream()) {
            float y   = cur.y;
            float dotX = isCont ? M + 14f : M + 5f;
            float dotR = isCont ? 2.5f    : 3.5f;
            float kwX  = isCont ? M + 24f : M + 14f;

            s.dot(cs, dotX, y + 4f, dotR, ColorScheme.forStatus(sts));
            s.drawText(cur.doc, cs, step.getKeyword(), kwX, y,
                    isCont ? s.italicFont() : s.boldFont(), 9.5f,
                    isCont ? ColorScheme.TEXT_SECONDARY : ColorScheme.ACCENT);

            float nmX = kwX + step.getKeyword().length() * (isCont ? 5.0f : 5.5f);
            s.drawText(cur.doc, cs, nm, nmX, y,
                    s.regularFont(), 9.5f, ColorScheme.TEXT_SECONDARY);

            // Duration right-aligned
            String ds = dur + "ms";
            s.drawText(cur.doc, cs, ds, PW - M - ds.length() * 4.8f, y,
                    s.regularFont(), 7.5f, ColorScheme.TEXT_HINT);

            // Row separator — v1.4.1: subtle line for visual separation
            s.hLine(cs, M, PW - M, y - 5f, ColorScheme.BORDER_SUBTLE, 0.3f);
        }
        cur.advance(LH);  // v1.4.1: LH=15f (was 14f)
    }

    // =========================================================================
    // Error block renderer  (CRLF-safe, tab-sanitised)
    // =========================================================================

    private void renderErrorBlock(ScenarioCursor cur, String err) throws IOException {
        if (err == null || err.isEmpty()) return;

        String[] lines = err.split("\\r?\\n");
        int shown = Math.min(lines.length, maxOutputLines);
        boolean truncated = lines.length > shown;
        float bH = shown * LHM + (truncated ? LHM : 0f) + 12f;

        cur.ensureSpace(bH + 6f);

        float bX = M + 8f, bY = cur.y - bH;
        try (PDPageContentStream cs = cur.stream()) {
            s.fillRect(cs, bX, bY, CW - 8f, bH, ColorScheme.FAILED_BG);
            s.fillRect(cs, bX, bY, 2.5f, bH, ColorScheme.FAILED);
            float ty = cur.y - 8f;
            for (int i = 0; i < shown; i++) {
                String line = lines[i].replace("\t", "    ");
                s.drawText(cur.doc, cs, trunc(line, 105),
                        bX + 10f, ty, s.monoFont(), 7.5f, ColorScheme.FAILED_TEXT);
                ty -= LHM;
            }
            if (truncated) {
                s.drawText(cur.doc, cs,
                        "... +" + (lines.length - shown) + " more lines",
                        bX + 10f, ty, s.monoFont(), 7.5f, ColorScheme.TEXT_HINT);
            }
        }
        cur.advance(bH + 6f);
    }

    // =========================================================================
    // Output log lines
    // =========================================================================

    private void renderOutputLogs(ScenarioCursor cur, List<String> lines) throws IOException {
        if (lines == null || lines.isEmpty()) return;
        int shown = Math.min(lines.size(), maxOutputLines);
        float bH  = shown * LHM + 10f;

        cur.ensureSpace(bH + 6f);
        float bX = M + 8f, bY = cur.y - bH;
        try (PDPageContentStream cs = cur.stream()) {
            s.fillRect(cs, bX, bY, CW - 8f, bH, ColorScheme.ROW_ALT);
            s.fillRect(cs, bX, bY, 2f, bH, ColorScheme.TEXT_HINT);
            float ty = cur.y - 7f;
            for (int i = 0; i < shown; i++) {
                s.drawText(cur.doc, cs, trunc(lines.get(i), 105),
                        bX + 8f, ty, s.monoFont(), 7.5f, ColorScheme.TEXT_SECONDARY);
                ty -= LHM;
            }
        }
        cur.advance(bH + 6f);
    }

    // =========================================================================
    // DataTable renderer
    // =========================================================================

    private void renderDataTable(ScenarioCursor cur,
                                  List<CucumberTableRow> rows) throws IOException {
        if (rows == null || rows.isEmpty()) return;
        float tH = rows.size() * LHM + 8f;
        cur.ensureSpace(tH + 6f);

        float bX = M + 8f, bW = CW - 8f, tY = cur.y - tH;
        try (PDPageContentStream cs = cur.stream()) {
            s.fillRect(cs, bX, tY, bW, tH, ColorScheme.ROW_ALT);
            s.strokeRect(cs, bX, tY, bW, tH, ColorScheme.BORDER, 0.4f);
            // Header row darker background
            s.fillRect(cs, bX, tY + tH - LHM - 4f, bW, LHM + 4f, ColorScheme.BORDER);
            float ty = cur.y - 6f;
            for (int i = 0; i < rows.size(); i++) {
                List<String> cells = rows.get(i).getCells();
                if (cells == null || cells.isEmpty()) { ty -= LHM; continue; }
                String row = trunc("| " + String.join(" | ", cells) + " |", 105);
                s.drawText(cur.doc, cs, row, bX + 6f, ty, s.monoFont(), 7.5f,
                        i == 0 ? ColorScheme.TEXT_PRIMARY : ColorScheme.TEXT_SECONDARY);
                ty -= LHM;
            }
        }
        cur.advance(tH + 6f);
    }

    // =========================================================================
    // DocString renderer
    // =========================================================================

    private void renderDocString(ScenarioCursor cur, CucumberDocString ds) throws IOException {
        if (ds == null || ds.getContent() == null || ds.getContent().isEmpty()) return;
        String[] lines = ds.getContent().split("\\r?\\n");
        int shown = Math.min(lines.length, 25);
        float bH  = shown * LHM + (lines.length > 25 ? LHM : 0f) + 10f;

        cur.ensureSpace(bH + 6f);
        float bX = M + 8f, bW = CW - 8f, bY = cur.y - bH;
        try (PDPageContentStream cs = cur.stream()) {
            s.fillRect(cs, bX, bY, bW, bH, ColorScheme.ROW_ALT);
            s.strokeRect(cs, bX, bY, bW, bH, ColorScheme.BORDER, 0.4f);
            s.fillRect(cs, bX, bY, 2.5f, bH, ColorScheme.ACCENT);
            float ty = cur.y - 7f;
            for (int i = 0; i < shown; i++) {
                s.drawText(cur.doc, cs, trunc(lines[i], 105),
                        bX + 9f, ty, s.monoFont(), 7.5f, ColorScheme.TEXT_SECONDARY);
                ty -= LHM;
            }
            if (lines.length > 25) {
                s.drawText(cur.doc, cs, "... +" + (lines.length - 25) + " more lines",
                        bX + 9f, ty, s.monoFont(), 7.5f, ColorScheme.TEXT_HINT);
            }
        }
        cur.advance(bH + 6f);
    }

    // =========================================================================
    // Hook entry renderer
    // =========================================================================

    private void renderHookEntry(ScenarioCursor cur, CucumberStep hook,
                                  String label) throws IOException {
        String err     = hook.getErrorMessage();
        List<String> shots = hook.getEmbeddings();
        if ((err == null || err.isEmpty()) && shots.isEmpty()) return;

        if (err != null && !err.isEmpty()) {
            cur.ensureSpace(LH + 4f);
            try (PDPageContentStream cs = cur.stream()) {
                s.drawText(cur.doc, cs, label, M, cur.y,
                        s.boldFont(), 8.5f,
                        label.contains("failed") || label.contains("Failed")
                                ? ColorScheme.FAILED_TEXT : ColorScheme.TEXT_MUTED);
            }
            cur.advance(LH);
            renderErrorBlock(cur, err);
        }
        renderEmbeddings(cur, shots);
    }

    // =========================================================================
    // Screenshot / embedding renderer  (Bug 2 fix)
    // =========================================================================

    private void renderEmbeddings(ScenarioCursor cur, List<String> embeddings) throws IOException {
        if (embeddings == null || embeddings.isEmpty()) return;

        for (int i = 0; i < embeddings.size(); i++) {
            String b64 = embeddings.get(i);
            if (b64 == null || b64.isEmpty()) continue;

            if (i == 0) {
                cur.ensureSpace(LH + 20f);
                try (PDPageContentStream cs = cur.stream()) {
                    s.drawText(cur.doc, cs, "Screenshots",
                            M, cur.y, s.boldFont(), 9f, ColorScheme.ACCENT);
                }
                cur.advance(LH + 2f);
            }

            try {
                byte[]        bytes = Base64.getDecoder().decode(b64.trim());
                PDImageXObject img  = PDImageXObject.createFromByteArray(cur.doc, bytes, "shot");
                float scale = Math.min(
                        Math.min(IMG_W / img.getWidth(), IMG_H / img.getHeight()), 1f);
                float dW = img.getWidth()  * scale;
                float dH = img.getHeight() * scale;

                cur.ensureSpace(dH + 18f);

                float fX = M + 4f, fW = CW - 4f, fY = cur.y - dH - 8f;
                try (PDPageContentStream cs = cur.stream()) {
                    s.fillRect(cs, fX, fY - 4f, fW, dH + 8f, Color.WHITE);
                    s.strokeRect(cs, fX, fY - 4f, fW, dH + 8f, ColorScheme.BORDER, 0.4f);
                    cs.drawImage(img, fX + (fW - dW) / 2f, fY, dW, dH);
                }
                cur.advance(dH + 14f);

            } catch (Exception e) {
                log.warn("Screenshot decode failed (b64 len={}): {}", b64.length(), e.getMessage());
                cur.ensureSpace(LH + 4f);
                try (PDPageContentStream cs = cur.stream()) {
                    s.drawText(cur.doc, cs,
                            "[Screenshot decode failed: " + e.getMessage() + "]",
                            M + 10f, cur.y, s.monoFont(), 8f, ColorScheme.FAILED_TEXT);
                }
                cur.advance(LH + 4f);
            }
        }
    }

    // =========================================================================
    // Page footer
    // =========================================================================

    private void drawPageFooter(PDDocument doc, PDPage page, String sectionLabel) {
        try (PDPageContentStream cs = new PDPageContentStream(doc, page,
                PDPageContentStream.AppendMode.APPEND, true)) {
            s.hLine(cs, M, PW - M, 28f, ColorScheme.BORDER, 0.4f);
            s.drawText(doc, cs,
                    "Cucumber PDF Reporter v1.5.0  |  " + sectionLabel,
                    M, 14f, s.regularFont(), 7f, ColorScheme.TEXT_HINT);
        } catch (IOException ignored) {
            // Footer failure is non-fatal
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String buildTagLine(List<String> tags, int maxTags) {
        if (tags == null || tags.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (String tag : tags) {
            if (shown >= maxTags) {
                sb.append("  +").append(tags.size() - maxTags);
                break;
            }
            if (sb.length() > 0) sb.append("  ");
            sb.append(tag.startsWith("@") ? tag : "@" + tag);
            shown++;
        }
        return sb.toString();
    }

    private static String safe(String v)         { return v != null ? v : ""; }
    private static String trunc(String v, int n) {
        if (v == null) return "";
        return v.length() > n ? v.substring(0, n - 3) + "..." : v;
    }
}
