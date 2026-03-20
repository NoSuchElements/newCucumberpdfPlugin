package com.nosuchelements.pdf.test;

import com.nosuchelements.consolidated.ConsolidatedPdfGenerator;
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
 * Integration tests for v1.2.0 completions:
 *   - FeaturesSection qTest Case ID column
 *   - ScenariosSection Tags column
 *   - FailureSummarySection (failures + all-pass banner)
 *   - Full consolidated report with all 7 sections
 *   - DashboardSection Case ID in at-a-glance table
 */
public class ConsolidatedCompletionIT {

    // -----------------------------------------------------------------------
    // Shared test JSON — 2 features, tagged, one failure
    // -----------------------------------------------------------------------
    private static final String FULL_JSON = "["
        + "{"
        + "\"name\":\"Payment Processing\","
        + "\"keyword\":\"Feature\","
        + "\"uri\":\"file:features/payment.feature\","
        + "\"tags\":[{\"name\":\"@QTEST_TC_2001\"},{\"name\":\"@billing\"}],"
        + "\"elements\":["
        + "  {\"name\":\"Successful credit card payment\","
        + "   \"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"tags\":[{\"name\":\"@smoke\"},{\"name\":\"@payment\"}],"
        + "   \"steps\":["
        + "    {\"keyword\":\"Given \",\"name\":\"I have a valid credit card\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":100000000}},"
        + "    {\"keyword\":\"When \",\"name\":\"I complete the purchase\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":300000000}},"
        + "    {\"keyword\":\"Then \",\"name\":\"I receive a confirmation email\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":200000000}}"
        + "   ]},"
        + "  {\"name\":\"Payment with expired card\","
        + "   \"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"tags\":[{\"name\":\"@regression\"}],"
        + "   \"steps\":["
        + "    {\"keyword\":\"Given \",\"name\":\"I have an expired credit card\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":100000000}},"
        + "    {\"keyword\":\"When \",\"name\":\"I attempt a payment\","
        + "     \"result\":{\"status\":\"failed\",\"duration\":400000000,"
        + "      \"error_message\":\"PaymentException: Card declined\\nat PaymentService.java:88\\nat CheckoutController.java:55\"}},"
        + "    {\"keyword\":\"Then \",\"name\":\"I see a declined error\","
        + "     \"result\":{\"status\":\"skipped\",\"duration\":0}}"
        + "   ]}"
        + "]}"
        + ","
        + "{"
        + "\"name\":\"User Registration\","
        + "\"keyword\":\"Feature\","
        + "\"uri\":\"file:features/registration.feature\","
        + "\"tags\":[{\"name\":\"@QTEST_TC_2002\"}],"
        + "\"elements\":["
        + "  {\"name\":\"Register new user\","
        + "   \"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"tags\":[{\"name\":\"@smoke\"}],"
        + "   \"steps\":["
        + "    {\"keyword\":\"Given \",\"name\":\"I am on the registration page\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":100000000}},"
        + "    {\"keyword\":\"When \",\"name\":\"I fill in valid details\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":200000000}},"
        + "    {\"keyword\":\"And \",\"name\":\"I submit the form\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":150000000}},"
        + "    {\"keyword\":\"Then \",\"name\":\"my account is created\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":80000000}}"
        + "   ]}"
        + "]}"
        + "]";

    // All-passing JSON for the all-pass banner test
    private static final String ALL_PASS_JSON = "[{"
        + "\"name\":\"Health Check\","
        + "\"keyword\":\"Feature\","
        + "\"uri\":\"health.feature\","
        + "\"tags\":[{\"name\":\"@smoke\"}],"
        + "\"elements\":[{"
        + "  \"name\":\"Server is reachable\","
        + "  \"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "  \"steps\":[{\"keyword\":\"When \",\"name\":\"I ping the server\","
        + "    \"result\":{\"status\":\"passed\",\"duration\":50000000}}]"
        + "}]}]";

    // JSON for And/But keyword test — has a failure
    private static final String AND_BUT_JSON = "[{"
        + "\"name\":\"And But Feature\","
        + "\"keyword\":\"Feature\","
        + "\"uri\":\"andbut.feature\","
        + "\"elements\":[{"
        + "  \"name\":\"Multi-step failure\","
        + "  \"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "  \"steps\":["
        + "   {\"keyword\":\"Given \",\"name\":\"I am logged in\","
        + "    \"result\":{\"status\":\"passed\",\"duration\":100000000}},"
        + "   {\"keyword\":\"And \",\"name\":\"the cart is empty\","
        + "    \"result\":{\"status\":\"passed\",\"duration\":50000000}},"
        + "   {\"keyword\":\"When \",\"name\":\"I add an item\","
        + "    \"result\":{\"status\":\"failed\",\"duration\":200000000,"
        + "     \"error_message\":\"ItemNotFoundException: SKU-999 not found\"}},"
        + "   {\"keyword\":\"But \",\"name\":\"the cart remains empty\","
        + "    \"result\":{\"status\":\"skipped\",\"duration\":0}}"
        + "  ]}]}]";

    // JSON for feature with no qTest tag
    private static final String NO_TAG_JSON = "[{"
        + "\"name\":\"Untagged Feature\","
        + "\"keyword\":\"Feature\","
        + "\"uri\":\"untagged.feature\","
        + "\"elements\":[{"
        + "  \"name\":\"A scenario\","
        + "  \"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "  \"steps\":[{"
        + "    \"keyword\":\"Given \",\"name\":\"something\","
        + "    \"result\":{\"status\":\"passed\",\"duration\":100000000}"
        + "  }]"
        + "}]}]";

    // -----------------------------------------------------------------------
    // 1. Full 7-section report — smoke test
    // -----------------------------------------------------------------------
    @Test
    public void fullSevenSectionReport() throws Exception {
        List<CucumberFeature> features = parse(FULL_JSON);
        assertEquals(2, features.size());

        File pdf = tempPdf("full-7-sections");
        new ConsolidatedPdfGenerator(
                true, true, true, true, false, true, true,
                20, "Full Report Test", "QTEST_TC_")
                .generateReport(features, pdf.getAbsolutePath());

        assertTrue("PDF must be non-empty", pdf.length() > 0);
        try (PDDocument doc = PDDocument.load(pdf)) {
            assertTrue("Must have at least 5 pages", doc.getNumberOfPages() >= 5);
            String text = new PDFTextStripper().getText(doc);
            // Dashboard
            assertTrue("Report title in dashboard", text.contains("Full Report Test"));
            // Failure summary
            assertTrue("Failure summary has failing scenario",
                    text.contains("Payment with expired card"));
            // Features — Case ID
            assertTrue("Features has TC-2001", text.contains("TC-2001"));
            assertTrue("Features has TC-2002", text.contains("TC-2002"));
            // Scenarios — Tags
            assertTrue("Scenarios section has smoke tag", text.contains("smoke"));
            // Page numbers
            assertTrue("Page 1 of N present", text.contains("Page 1 of"));
        }
    }

    // -----------------------------------------------------------------------
    // 2. FeaturesSection — Case ID rendered for tagged features
    // -----------------------------------------------------------------------
    @Test
    public void featuresSectionRendersQTestCaseId() throws Exception {
        List<CucumberFeature> features = parse(FULL_JSON);
        File pdf = tempPdf("features-caseid");
        new ConsolidatedPdfGenerator(
                false, true, false, false, false, false, false,
                20, "Features Test", "QTEST_TC_")
                .generateReport(features, pdf.getAbsolutePath());

        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("Case ID column header present", text.contains("Case ID"));
            assertTrue("TC-2001 present", text.contains("TC-2001"));
            assertTrue("TC-2002 present", text.contains("TC-2002"));
        }
    }

    // -----------------------------------------------------------------------
    // 3. FeaturesSection — feature with no matching tag
    // -----------------------------------------------------------------------
    @Test
    public void featuresSectionNoTagFeature() throws Exception {
        List<CucumberFeature> features = parse(NO_TAG_JSON);
        File pdf = tempPdf("features-notag");
        new ConsolidatedPdfGenerator(
                false, true, false, false, false, false, false,
                20, "No Tag Test", "QTEST_TC_")
                .generateReport(features, pdf.getAbsolutePath());

        assertTrue("PDF must be non-empty", pdf.length() > 0);
        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("Feature name present", text.contains("Untagged Feature"));
            // TC- prefix should NOT appear for this feature
            assertFalse("No TC- prefix for untagged feature", text.contains("TC-"));
        }
    }

    // -----------------------------------------------------------------------
    // 4. ScenariosSection — Tags column rendered
    // -----------------------------------------------------------------------
    @Test
    public void scenariosSectionRendersTagsColumn() throws Exception {
        List<CucumberFeature> features = parse(FULL_JSON);
        File pdf = tempPdf("scenarios-tags");
        new ConsolidatedPdfGenerator(
                false, false, true, false, false, false, false,
                20, "Scenarios Test", "QTEST_TC_")
                .generateReport(features, pdf.getAbsolutePath());

        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("Tags column header present", text.contains("Tags"));
            // @smoke tag should appear stripped of @ in column
            assertTrue("smoke tag visible", text.contains("smoke"));
            // regression tag should appear
            assertTrue("regression tag visible", text.contains("regression"));
        }
    }

    // -----------------------------------------------------------------------
    // 5. FailureSummarySection — shows failing scenarios with error messages
    // -----------------------------------------------------------------------
    @Test
    public void failureSummarySectionShowsFailingScenarios() throws Exception {
        List<CucumberFeature> features = parse(FULL_JSON);
        File pdf = tempPdf("failure-summary");
        new ConsolidatedPdfGenerator(
                false, false, false, false, false, false, true,
                20, "Failure Summary Test", "QTEST_TC_")
                .generateReport(features, pdf.getAbsolutePath());

        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("Failure Summary header present",
                    text.contains("Failure Summary"));
            assertTrue("Failing scenario name present",
                    text.contains("Payment with expired card"));
            assertTrue("Error message present",
                    text.contains("PaymentException"));
            // The passing scenario should NOT appear in failure summary
            assertFalse("Passing scenario not in failure summary",
                    text.contains("Successful credit card payment"));
        }
    }

    // -----------------------------------------------------------------------
    // 6. FailureSummarySection — all-pass banner when no failures
    // -----------------------------------------------------------------------
    @Test
    public void failureSummarySectionAllPassBanner() throws Exception {
        List<CucumberFeature> features = parse(ALL_PASS_JSON);
        File pdf = tempPdf("failure-summary-allpass");
        new ConsolidatedPdfGenerator(
                false, false, false, false, false, false, true,
                20, "All Pass Test", "QTEST_TC_")
                .generateReport(features, pdf.getAbsolutePath());

        assertTrue("PDF must be non-empty", pdf.length() > 0);
        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("All-pass message present",
                    text.contains("All scenarios passed"));
        }
    }

    // -----------------------------------------------------------------------
    // 7. Section ordering — Dashboard page 1, Failure Summary page 2
    // -----------------------------------------------------------------------
    @Test
    public void dashboardIsPage1AndFailureSummaryIsPage2() throws Exception {
        List<CucumberFeature> features = parse(FULL_JSON);
        File pdf = tempPdf("section-order");
        new ConsolidatedPdfGenerator(
                true, false, false, false, false, false, true,
                20, "Order Test", "QTEST_TC_")
                .generateReport(features, pdf.getAbsolutePath());

        try (PDDocument doc = PDDocument.load(pdf)) {
            assertTrue("Should have at least 2 pages",
                    doc.getNumberOfPages() >= 2);
            PDFTextStripper st = new PDFTextStripper();

            st.setStartPage(1); st.setEndPage(1);
            String p1 = st.getText(doc);
            assertTrue("Page 1 is Dashboard (contains report title)",
                    p1.contains("Order Test"));

            st.setStartPage(2); st.setEndPage(2);
            String p2 = st.getText(doc);
            assertTrue("Page 2 is Failure Summary",
                    p2.contains("Failure Summary"));
        }
    }

    // -----------------------------------------------------------------------
    // 8. Backward-compat — 8-arg constructor still works
    // -----------------------------------------------------------------------
    @Test
    public void eightArgConstructorBackwardCompat() throws Exception {
        List<CucumberFeature> features = parse(ALL_PASS_JSON);
        File pdf = tempPdf("8arg-compat");
        // Pre-v1.2.0 constructor signature
        new ConsolidatedPdfGenerator(
                true, true, true, false, false,
                20, "Compat Test", "QTEST_TC_")
                .generateReport(features, pdf.getAbsolutePath());
        assertTrue("PDF must be non-empty", pdf.length() > 0);
    }

    // -----------------------------------------------------------------------
    // 9. FailureSummarySection — And/But keywords render without exception
    // -----------------------------------------------------------------------
    @Test
    public void failureSummarySectionAndButKeywords() throws Exception {
        List<CucumberFeature> features = parse(AND_BUT_JSON);
        File pdf = tempPdf("andbut-failure");
        new ConsolidatedPdfGenerator(
                false, false, false, false, false, false, true,
                20, "AndBut Test", "QTEST_TC_")
                .generateReport(features, pdf.getAbsolutePath());

        assertTrue("PDF must be non-empty", pdf.length() > 0);
        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("Error message present",
                    text.contains("ItemNotFoundException"));
            assertTrue("Scenario name present",
                    text.contains("Multi-step failure"));
        }
    }

    // -----------------------------------------------------------------------
    // 10. DashboardSection — Case ID column in at-a-glance table
    // -----------------------------------------------------------------------
    @Test
    public void dashboardSectionShowsCaseIdInGlanceTable() throws Exception {
        List<CucumberFeature> features = parse(FULL_JSON);
        File pdf = tempPdf("dashboard-caseid");
        new ConsolidatedPdfGenerator(
                true, false, false, false, false, false, false,
                20, "Dashboard Case ID Test", "QTEST_TC_")
                .generateReport(features, pdf.getAbsolutePath());

        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            // Dashboard at-a-glance table strips prefix, shows just "2001" not "TC-2001"
            // (DashboardSection returns bare number for compact display)
            assertTrue("Dashboard has report title",
                    text.contains("Dashboard Case ID Test"));
            // Case ID column header present
            assertTrue("Case ID column in dashboard",
                    text.contains("Case ID"));
        }
    }

    // -----------------------------------------------------------------------
    // 11. Page numbering — every page has "Page N of T"
    // -----------------------------------------------------------------------
    @Test
    public void pageNumbersOnEveryPage() throws Exception {
        List<CucumberFeature> features = parse(FULL_JSON);
        File pdf = tempPdf("page-numbers-completion");
        new ConsolidatedPdfGenerator(
                true, true, true, false, false, false, true,
                20, "Page Number Test", "QTEST_TC_")
                .generateReport(features, pdf.getAbsolutePath());

        try (PDDocument doc = PDDocument.load(pdf)) {
            int total = doc.getNumberOfPages();
            assertTrue("Must have at least 3 pages", total >= 3);
            PDFTextStripper st = new PDFTextStripper();
            for (int i = 1; i <= total; i++) {
                st.setStartPage(i); st.setEndPage(i);
                String page = st.getText(doc);
                assertTrue("Page " + i + " should have page number",
                        page.contains("Page " + i + " of " + total));
            }
        }
    }

    // -----------------------------------------------------------------------
    // 12. TagStatsSection — renders with no exception, tag names visible
    // -----------------------------------------------------------------------
    @Test
    public void tagStatsSectionRendersCorrectly() throws Exception {
        List<CucumberFeature> features = parse(FULL_JSON);
        File pdf = tempPdf("tag-stats-completion");
        new ConsolidatedPdfGenerator(
                false, false, false, false, false, true, false,
                20, "Tag Stats Test", "QTEST_TC_")
                .generateReport(features, pdf.getAbsolutePath());

        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("Tag Stats header present",
                    text.contains("Tag Statistics"));
            assertTrue("smoke tag in stats", text.contains("@smoke"));
            assertTrue("regression tag in stats", text.contains("@regression"));
            assertTrue("billing tag in stats", text.contains("@billing"));
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------
    private static List<CucumberFeature> parse(String json) throws Exception {
        File f = Files.createTempFile("completion-test", ".json").toFile();
        try (FileWriter w = new FileWriter(f)) { w.write(json); }
        return new CucumberJsonParser(false).parseJsonFile(f.getAbsolutePath());
    }

    private static File tempPdf(String prefix) throws Exception {
        return Files.createTempFile(prefix, ".pdf").toFile();
    }
}
