package com.nosuchelements.pdf.test;

import com.nosuchelements.consolidated.ConsolidatedPdfGenerator;
import com.nosuchelements.consolidated.ReportStats;
import com.nosuchelements.cucumber.CucumberJsonParser;
import com.nosuchelements.cucumber.model.CucumberFeature;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Integration tests for the consolidated PDF report generator.
 */
public class ConsolidatedPdfGeneratorIT {

    // A two-feature, multi-scenario JSON to exercise all sections
    private static final String MULTI_FEATURE_JSON = "["
        // Feature 1 — all pass
        + "{"
        + "\"name\":\"User Authentication\","
        + "\"keyword\":\"Feature\","
        + "\"uri\":\"file:features/auth.feature\","
        + "\"tags\":[{\"name\":\"@QTEST_TC_1001\"}],"
        + "\"elements\":["
        + "  {\"name\":\"Login with valid credentials\","
        + "   \"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"tags\":[{\"name\":\"@smoke\"}],"
        + "   \"steps\":["
        + "     {\"keyword\":\"Given \",\"name\":\"I am on the login page\","
        + "      \"result\":{\"status\":\"passed\",\"duration\":500000000}},"
        + "     {\"keyword\":\"When \",\"name\":\"I enter valid credentials\","
        + "      \"result\":{\"status\":\"passed\",\"duration\":300000000}},"
        + "     {\"keyword\":\"Then \",\"name\":\"I should be logged in\","
        + "      \"result\":{\"status\":\"passed\",\"duration\":200000000}}"
        + "   ]},"
        + "  {\"name\":\"Login with invalid password\","
        + "   \"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"steps\":["
        + "     {\"keyword\":\"Given \",\"name\":\"I am on the login page\","
        + "      \"result\":{\"status\":\"passed\",\"duration\":400000000}},"
        + "     {\"keyword\":\"When \",\"name\":\"I enter wrong password\","
        + "      \"result\":{\"status\":\"passed\",\"duration\":250000000}},"
        + "     {\"keyword\":\"Then \",\"name\":\"I should see error message\","
        + "      \"result\":{\"status\":\"passed\",\"duration\":180000000}}"
        + "   ]}"
        + "]}"
        // Feature 2 — mixed pass/fail
        + ","
        + "{"
        + "\"name\":\"Shopping Cart\","
        + "\"keyword\":\"Feature\","
        + "\"uri\":\"file:features/cart.feature\","
        + "\"tags\":[{\"name\":\"@QTEST_TC_1002\"}],"
        + "\"elements\":["
        + "  {\"name\":\"Add item to cart\","
        + "   \"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"steps\":["
        + "     {\"keyword\":\"Given \",\"name\":\"I have an empty cart\","
        + "      \"result\":{\"status\":\"passed\",\"duration\":100000000}},"
        + "     {\"keyword\":\"When \",\"name\":\"I add a product\","
        + "      \"result\":{\"status\":\"passed\",\"duration\":200000000}},"
        + "     {\"keyword\":\"Then \",\"name\":\"Cart should have 1 item\","
        + "      \"result\":{\"status\":\"passed\",\"duration\":150000000}}"
        + "   ]},"
        + "  {\"name\":\"Checkout with expired card\","
        + "   \"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"steps\":["
        + "     {\"keyword\":\"Given \",\"name\":\"I have items in my cart\","
        + "      \"result\":{\"status\":\"passed\",\"duration\":100000000}},"
        + "     {\"keyword\":\"When \",\"name\":\"I enter an expired credit card\","
        + "      \"result\":{\"status\":\"failed\",\"duration\":500000000,"
        + "       \"error_message\":\"AssertionError: Expected payment to fail\\n\\tat PaymentService.charge(PaymentService.java:88)\"}},"
        + "     {\"keyword\":\"Then \",\"name\":\"I should see declined message\","
        + "      \"result\":{\"status\":\"skipped\",\"duration\":0}}"
        + "   ]}"
        + "]}"
        + "]";

    // -----------------------------------------------------------------------
    // 1. Basic generation — all sections on
    // -----------------------------------------------------------------------

    @Test
    public void generatesConsolidatedPdfWithAllSections() throws Exception {
        File jsonFile = tempJson(MULTI_FEATURE_JSON);
        CucumberJsonParser parser = new CucumberJsonParser(false);
        List<CucumberFeature> features = parser.parseJsonFile(jsonFile.getAbsolutePath());

        assertEquals("Should parse 2 features", 2, features.size());

        File pdf = tempPdf("consolidated-all");
        new ConsolidatedPdfGenerator().generateReport(features, pdf.getAbsolutePath());

        assertTrue("PDF should be non-empty", pdf.length() > 0);

        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("Should contain Dashboard content",   text.contains("Cucumber Test Report"));
            assertTrue("Should contain feature name (Auth)", text.contains("User Authentication"));
            assertTrue("Should contain feature name (Cart)", text.contains("Shopping Cart"));
            assertTrue("Should contain a scenario name",     text.contains("Login with valid credentials"));
        }
    }

    // -----------------------------------------------------------------------
    // 2. Dashboard only
    // -----------------------------------------------------------------------

    @Test
    public void generatesConsolidatedPdfDashboardOnly() throws Exception {
        File jsonFile = tempJson(MULTI_FEATURE_JSON);
        CucumberJsonParser parser = new CucumberJsonParser(false);
        List<CucumberFeature> features = parser.parseJsonFile(jsonFile.getAbsolutePath());

        File pdf = tempPdf("consolidated-dashboard-only");
        // Use 10-arg to explicitly disable all sections except Dashboard,
        // so this test truly produces exactly 1 page.
        ConsolidatedPdfGenerator gen = new ConsolidatedPdfGenerator(
                true,   // displayDashboard
                false,  // displayFeatures
                false,  // displayScenarios
                false,  // displayDetailed
                false,  // displayExpanded
                false,  // displayTagStats
                false,  // displayFailureSummary
                20, "Dashboard Only Report", "QTEST_TC_");
        gen.generateReport(features, pdf.getAbsolutePath());

        assertTrue("PDF should be non-empty", pdf.length() > 0);
        try (PDDocument doc = PDDocument.load(pdf)) {
            // Dashboard-only = exactly 1 page
            assertEquals("Should be 1 page", 1, doc.getNumberOfPages());
        }
    }

    // -----------------------------------------------------------------------
    // 3. Consolidated with expanded (screenshots)
    // -----------------------------------------------------------------------

    private static final String WITH_SCREENSHOT_JSON = "[{"
        + "\"name\":\"Screenshot Feature\","
        + "\"keyword\":\"Feature\","
        + "\"uri\":\"screenshots.feature\","
        + "\"tags\":[{\"name\":\"@QTEST_TC_1206\"}],"
        + "\"elements\":[{"
        + "  \"name\":\"Scenario with after-hook screenshot\","
        + "  \"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "  \"steps\":[{"
        + "    \"keyword\":\"Given \",\"name\":\"I navigate to a page\","
        + "    \"result\":{\"status\":\"passed\",\"duration\":1000000000}"
        + "  }],"
        + "  \"after\":[{"
        + "    \"result\":{\"status\":\"passed\",\"duration\":50000000,"
        + "     \"embeddings\":[{"
        + "       \"mime_type\":\"image/png\","
        + "       \"data\":\"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==\""
        + "     }]}"
        + "  }]"
        + "}]}]";

    @Test
    public void generatesConsolidatedPdfWithExpanded() throws Exception {
        File jsonFile = tempJson(WITH_SCREENSHOT_JSON);
        CucumberJsonParser parser = new CucumberJsonParser(false);
        List<CucumberFeature> features = parser.parseJsonFile(jsonFile.getAbsolutePath());

        // Verify screenshot found
        assertEquals(1, features.get(0).getScenarios().get(0).getAllScreenshots().size());

        File pdf = tempPdf("consolidated-expanded");
        // 8-arg constructor: displayTagStats + displayFailureSummary both default true
        ConsolidatedPdfGenerator gen = new ConsolidatedPdfGenerator(
                true, true, true, true, true, 20,
                "Test with Screenshots", "QTEST_TC_");
        gen.generateReport(features, pdf.getAbsolutePath());

        assertTrue("PDF should be non-empty", pdf.length() > 0);
    }

    // -----------------------------------------------------------------------
    // 4. ReportStats accuracy
    // -----------------------------------------------------------------------

    @Test
    public void reportStatsAreAccurate() throws Exception {
        File jsonFile = tempJson(MULTI_FEATURE_JSON);
        CucumberJsonParser parser = new CucumberJsonParser(false);
        List<CucumberFeature> features = parser.parseJsonFile(jsonFile.getAbsolutePath());

        ReportStats stats = ReportStats.compute(features);

        assertEquals("2 total features",      2, stats.totalFeatures);
        assertEquals("4 total scenarios",     4, stats.totalScenarios);
        assertEquals("1 failed scenario",     1, stats.failedScenarios);
        assertEquals("12 total steps",       12, stats.totalSteps);
        assertEquals("1 failed step",         1, stats.failedSteps);
        assertEquals("1 skipped step",        1, stats.skippedSteps);
        assertEquals("10 passed steps",      10, stats.passedSteps);
        assertEquals("Overall should be FAILED", "FAILED", stats.getOverallStatus());
    }

    // -----------------------------------------------------------------------
    // 5. Custom report title
    // -----------------------------------------------------------------------

    @Test
    public void customReportTitleAppearsInPdf() throws Exception {
        File jsonFile = tempJson(MULTI_FEATURE_JSON);
        CucumberJsonParser parser = new CucumberJsonParser(false);
        List<CucumberFeature> features = parser.parseJsonFile(jsonFile.getAbsolutePath());

        File pdf = tempPdf("consolidated-custom-title");
        // 8-arg: displayTagStats + displayFailureSummary default true
        ConsolidatedPdfGenerator gen = new ConsolidatedPdfGenerator(
                true, false, false, false, false, 20,
                "Sprint 42 Regression Run", "QTEST_TC_");
        gen.generateReport(features, pdf.getAbsolutePath());

        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("Custom title should appear in PDF",
                    text.contains("Sprint 42 Regression Run"));
        }
    }

    // -----------------------------------------------------------------------
    // 6. formatDuration helper
    // -----------------------------------------------------------------------

    @Test
    public void reportStatsDurationFormatsCorrectly() {
        ReportStats s = ReportStats.compute(List.of());
        // Zero features → zero ms
        assertEquals("0ms", s.formatDuration());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------
    private static File tempJson(String content) throws Exception {
        File f = Files.createTempFile("cucumber-consolidated-test", ".json").toFile();
        try (FileWriter w = new FileWriter(f)) { w.write(content); }
        return f;
    }
    private static File tempPdf(String prefix) throws Exception {
        return Files.createTempFile(prefix, ".pdf").toFile();
    }
}
