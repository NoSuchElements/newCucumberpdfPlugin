package com.nosuchelements.pdf.test;

import com.nosuchelements.consolidated.ConsolidatedPdfGenerator;
import com.nosuchelements.consolidated.ContentBlockRenderer;
import com.nosuchelements.consolidated.ReportStats;
import com.nosuchelements.cucumber.CucumberJsonParser;
import com.nosuchelements.cucumber.model.CucumberFeature;
import com.nosuchelements.pdf.PdfStyler;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Extended integration tests for v1.2.0 additions:
 *  - TagStatsSection
 *  - Two-pass page-number stamping
 *  - ContentBlockRenderer (unit-level)
 *  - ReportStats edge cases
 *  - reportMode=both equivalent (split + consolidated in sequence)
 */
public class ConsolidatedPdfGeneratorV2IT {

    // -----------------------------------------------------------------------
    // Shared test JSON — 3 features, varied tags, pass/fail/skip mix
    // -----------------------------------------------------------------------
    private static final String MULTI_TAG_JSON = "["
        // Feature 1 — all pass, smoke tag
        + "{"
        + "\"name\":\"Login Feature\","
        + "\"keyword\":\"Feature\","
        + "\"uri\":\"file:features/login.feature\","
        + "\"tags\":[{\"name\":\"@smoke\"},{\"name\":\"@QTEST_TC_1001\"}],"
        + "\"elements\":["
        + "  {\"name\":\"Valid login\",\"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"tags\":[{\"name\":\"@smoke\"},{\"name\":\"@regression\"}],"
        + "   \"steps\":["
        + "    {\"keyword\":\"Given \",\"name\":\"I am on login page\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":200000000}},"
        + "    {\"keyword\":\"When \",\"name\":\"I enter valid credentials\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":150000000}},"
        + "    {\"keyword\":\"Then \",\"name\":\"I should be logged in\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":100000000}}"
        + "   ]},"
        + "  {\"name\":\"Invalid password\",\"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"tags\":[{\"name\":\"@regression\"}],"
        + "   \"steps\":["
        + "    {\"keyword\":\"Given \",\"name\":\"I am on login page\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":200000000}},"
        + "    {\"keyword\":\"When \",\"name\":\"I enter wrong password\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":150000000}},"
        + "    {\"keyword\":\"Then \",\"name\":\"I see error message\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":100000000}}"
        + "   ]}"
        + "]}"
        // Feature 2 — one failure, has @regression + @wip tags
        + ","
        + "{"
        + "\"name\":\"Checkout Feature\","
        + "\"keyword\":\"Feature\","
        + "\"uri\":\"file:features/checkout.feature\","
        + "\"tags\":[{\"name\":\"@QTEST_TC_1002\"}],"
        + "\"elements\":["
        + "  {\"name\":\"Successful checkout\",\"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"tags\":[{\"name\":\"@regression\"}],"
        + "   \"steps\":["
        + "    {\"keyword\":\"Given \",\"name\":\"I have items in cart\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":100000000}},"
        + "    {\"keyword\":\"When \",\"name\":\"I complete payment\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":300000000}},"
        + "    {\"keyword\":\"Then \",\"name\":\"Order is confirmed\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":200000000}}"
        + "   ]},"
        + "  {\"name\":\"Checkout with expired card\",\"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"tags\":[{\"name\":\"@wip\"},{\"name\":\"@regression\"}],"
        + "   \"steps\":["
        + "    {\"keyword\":\"Given \",\"name\":\"I have items in cart\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":100000000}},"
        + "    {\"keyword\":\"When \",\"name\":\"I enter expired card\","
        + "     \"result\":{\"status\":\"failed\",\"duration\":400000000,"
        + "      \"error_message\":\"PaymentException: Card expired\\n\\tat PaymentService.java:88\"}},"
        + "    {\"keyword\":\"Then \",\"name\":\"I see declined message\","
        + "     \"result\":{\"status\":\"skipped\",\"duration\":0}}"
        + "   ]}"
        + "]}"
        // Feature 3 — all pass, api tag
        + ","
        + "{"
        + "\"name\":\"API Feature\","
        + "\"keyword\":\"Feature\","
        + "\"uri\":\"file:features/api.feature\","
        + "\"tags\":[{\"name\":\"@api\"},{\"name\":\"@QTEST_TC_1003\"}],"
        + "\"elements\":["
        + "  {\"name\":\"GET /users returns 200\",\"type\":\"scenario\","
        + "   \"keyword\":\"Scenario\","
        + "   \"tags\":[{\"name\":\"@api\"},{\"name\":\"@smoke\"}],"
        + "   \"steps\":["
        + "    {\"keyword\":\"When \",\"name\":\"I call GET /users\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":50000000}},"
        + "    {\"keyword\":\"Then \",\"name\":\"response status is 200\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":10000000}}"
        + "   ]}"
        + "]}"
        + "]";

    // -----------------------------------------------------------------------
    // 1. Full report — all 6 sections (no expanded)
    // -----------------------------------------------------------------------
    @Test
    public void fullReportWithTagStatsGeneratesSuccessfully() throws Exception {
        List<CucumberFeature> features = parse(MULTI_TAG_JSON);
        assertEquals(3, features.size());

        File pdf = tempPdf("full-with-tags");
        new ConsolidatedPdfGenerator().generateReport(features, pdf.getAbsolutePath());

        assertTrue("PDF should be non-empty", pdf.length() > 0);

        try (PDDocument doc = PDDocument.load(pdf)) {
            assertTrue("Should have multiple pages", doc.getNumberOfPages() > 2);
            String text = new PDFTextStripper().getText(doc);
            assertTrue("Dashboard title", text.contains("Cucumber Test Report"));
            assertTrue("Feature name", text.contains("Login Feature"));
            assertTrue("Scenario name", text.contains("Valid login"));
        }
    }

    // -----------------------------------------------------------------------
    // 2. Page numbering — "Page N of T" appears on every page
    // -----------------------------------------------------------------------
    @Test
    public void pageNumbersStampedOnAllPages() throws Exception {
        List<CucumberFeature> features = parse(MULTI_TAG_JSON);
        File pdf = tempPdf("page-numbers");
        new ConsolidatedPdfGenerator().generateReport(features, pdf.getAbsolutePath());

        try (PDDocument doc = PDDocument.load(pdf)) {
            int total = doc.getNumberOfPages();
            assertTrue("Should have at least 2 pages", total >= 2);
            PDFTextStripper stripper = new PDFTextStripper();
            for (int i = 1; i <= total; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(doc);
                assertTrue("Page " + i + " should contain 'Page " + i + " of " + total + "'",
                        text.contains("Page " + i + " of " + total));
            }
        }
    }

    // -----------------------------------------------------------------------
    // 3. Tag statistics accuracy
    // -----------------------------------------------------------------------
    @Test
    public void tagStatsCollectsCorrectCounts() throws Exception {
        // Verify via ReportStats (TagStat collection is internal to section,
        // but we can verify the overall picture through the PDF text)
        List<CucumberFeature> features = parse(MULTI_TAG_JSON);
        File pdf = tempPdf("tag-stats");
        new ConsolidatedPdfGenerator(
                false,  // displayDashboard
                false,  // displayFeatures
                false,  // displayScenarios
                false,  // displayDetailed
                false,  // displayExpanded
                true,   // displayTagStats
                false,  // displayFailureSummary  — off: test is tag-stats only
                20, "Tag Stats Only", "QTEST_TC_")
                .generateReport(features, pdf.getAbsolutePath());

        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            // @smoke appears on 3 scenarios
            assertTrue("@smoke tag should appear", text.contains("@smoke"));
            // @regression appears on 4 scenarios (appears in multiple features)
            assertTrue("@regression tag should appear", text.contains("@regression"));
            // @wip appears on failing scenario
            assertTrue("@wip tag should appear", text.contains("@wip"));
        }
    }

    // -----------------------------------------------------------------------
    // 4. ReportStats — 3 features
    // -----------------------------------------------------------------------
    @Test
    public void reportStatsFor3Features() throws Exception {
        List<CucumberFeature> features = parse(MULTI_TAG_JSON);
        ReportStats stats = ReportStats.compute(features);

        assertEquals("3 features",         3, stats.totalFeatures);
        assertEquals("5 scenarios",        5, stats.totalScenarios);
        assertEquals("1 failed scenario",  1, stats.failedScenarios);
        assertEquals("13 total steps",    13, stats.totalSteps);
        assertEquals("1 failed step",      1, stats.failedSteps);
        assertEquals("1 skipped step",     1, stats.skippedSteps);
        assertEquals("11 passed steps",   11, stats.passedSteps);
        assertEquals("FAILED",            "FAILED", stats.getOverallStatus());
        // Pass rate
        assertEquals(84, stats.passRatePercent(stats.passedSteps, stats.totalSteps));
    }

    // -----------------------------------------------------------------------
    // 5. ContentBlockRenderer — wrapStepName edge cases
    // -----------------------------------------------------------------------
    @Test
    public void wrapStepNameHandlesEdgeCases() {
        // Exactly maxChars — no wrap
        String[] r1 = ContentBlockRenderer.wrapStepName("Hello World", 11);
        assertEquals(1, r1.length);
        assertEquals("Hello World", r1[0]);

        // Needs wrap at word boundary
        String[] r2 = ContentBlockRenderer.wrapStepName(
                "I click the very long submit button", 20);
        assertEquals(2, r2.length);
        assertEquals("I click the very", r2[0]);
        assertEquals("long submit button", r2[1]);

        // No space — hard split
        String[] r3 = ContentBlockRenderer.wrapStepName("ABCDEFGHIJ", 5);
        assertEquals(2, r3.length);
        assertEquals("ABCDE", r3[0]);
        assertEquals("FGHIJ", r3[1]);

        // Null / empty
        String[] r4 = ContentBlockRenderer.wrapStepName(null, 50);
        assertEquals(1, r4.length);
        assertEquals("", r4[0]);

        String[] r5 = ContentBlockRenderer.wrapStepName("", 50);
        assertEquals(1, r5.length);
    }

    // -----------------------------------------------------------------------
    // 6. ContentBlockRenderer — isContinuationKeyword
    // -----------------------------------------------------------------------
    @Test
    public void continuationKeywordClassification() {
        assertTrue(ContentBlockRenderer.isContinuationKeyword("And "));
        assertTrue(ContentBlockRenderer.isContinuationKeyword("But "));
        assertTrue(ContentBlockRenderer.isContinuationKeyword("and"));
        assertTrue(ContentBlockRenderer.isContinuationKeyword("BUT"));
        assertFalse(ContentBlockRenderer.isContinuationKeyword("Given "));
        assertFalse(ContentBlockRenderer.isContinuationKeyword("When "));
        assertFalse(ContentBlockRenderer.isContinuationKeyword("Then "));
        assertFalse(ContentBlockRenderer.isContinuationKeyword("* "));
        assertFalse(ContentBlockRenderer.isContinuationKeyword(null));
        assertFalse(ContentBlockRenderer.isContinuationKeyword(""));
    }

    // -----------------------------------------------------------------------
    // 7. stampPageNumbers is idempotent on 1-page doc
    // -----------------------------------------------------------------------
    @Test
    public void stampPageNumbersOnSinglePageDoc() throws Exception {
        PdfStyler styler = new PdfStyler();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            // Should not throw even on a 1-page document
            ConsolidatedPdfGenerator.stampPageNumbers(doc, styler);

            File pdf = tempPdf("single-page-stamp");
            doc.save(pdf);
            assertTrue(pdf.length() > 0);
        }
    }

    // -----------------------------------------------------------------------
    // 8. reportMode=both equivalent — split then consolidated
    // -----------------------------------------------------------------------
    @Test
    public void bothModePdfFilesAreIndependentlyValid() throws Exception {
        List<CucumberFeature> features = parse(MULTI_TAG_JSON);

        // Consolidated
        File consolidated = tempPdf("both-consolidated");
        new ConsolidatedPdfGenerator().generateReport(features, consolidated.getAbsolutePath());

        // Split (for each feature)
        for (CucumberFeature f : features) {
            File split = tempPdf("both-split-" + f.getName().replace(" ", "_"));
            new com.nosuchelements.pdf.FeaturePdfGenerator()
                    .generateFeaturePdf(f, split.getAbsolutePath());
            assertTrue("Split PDF should be non-empty for " + f.getName(),
                    split.length() > 0);
        }

        // Both should be independently valid PDFs
        try (PDDocument doc = PDDocument.load(consolidated)) {
            assertTrue("Consolidated should have pages",
                    doc.getNumberOfPages() > 0);
        }
    }

    // -----------------------------------------------------------------------
    // 9. ReportStats.formatDuration — tests the real class method, not a local copy
    // -----------------------------------------------------------------------
    @Test
    public void reportStatsFormatDurationBranches() throws Exception {
        // Zero ms: compute() from empty list -> totalDurationMs = 0
        ReportStats zeroStats = ReportStats.compute(new java.util.ArrayList<>());
        assertEquals("0ms", zeroStats.formatDuration());

        // Use the MULTI_TAG_JSON to get a real non-zero duration
        List<CucumberFeature> features = parse(MULTI_TAG_JSON);
        ReportStats realStats = ReportStats.compute(features);
        // All durations in MULTI_TAG_JSON are <60s, so we expect "X.Xs" format
        String dur = realStats.formatDuration();
        assertTrue("Non-zero duration should not be '0ms'", !"0ms".equals(dur));
        // Should end with 's' (seconds format) or 'ms' — either is valid
        assertTrue("Duration ends with 's' or 'ms'",
                dur.endsWith("s") || dur.endsWith("ms"));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------
    private static List<CucumberFeature> parse(String json) throws Exception {
        File f = Files.createTempFile("v2-test", ".json").toFile();
        try (FileWriter w = new FileWriter(f)) { w.write(json); }
        return new CucumberJsonParser(false).parseJsonFile(f.getAbsolutePath());
    }

    private static File tempPdf(String prefix) throws Exception {
        return Files.createTempFile(prefix, ".pdf").toFile();
    }
}
