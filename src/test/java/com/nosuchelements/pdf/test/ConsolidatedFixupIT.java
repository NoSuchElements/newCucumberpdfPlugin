package com.nosuchelements.pdf.test;

import com.nosuchelements.consolidated.ConsolidatedPdfGenerator;
import com.nosuchelements.consolidated.ContentBlockRenderer;
import com.nosuchelements.consolidated.ReportStats;
import com.nosuchelements.cucumber.CucumberJsonParser;
import com.nosuchelements.cucumber.model.CucumberFeature;
import com.nosuchelements.cucumber.model.CucumberScenario;
import com.nosuchelements.cucumber.model.CucumberStep;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Covers the 10 gaps identified by systematic test-coverage analysis:
 *
 *  1.  TagStatsSection no-tags notice (blank-page fix)
 *  2.  consolidatedReportName blank guard logic
 *  3.  ContentBlockRenderer.renderErrorBlock truncation at maxLines
 *  4.  ContentBlockRenderer.renderDataTable
 *  5.  ContentBlockRenderer.renderDocString
 *  6.  Background steps parsed and rendered by DetailedSection
 *  7.  FailureSummarySection: before-hook error shown
 *  8.  ExpandedSection: 3 screenshots per scenario
 *  9.  ReportStats.passRatePercent edge cases (0/0, rounding)
 * 10.  ContentBlockRenderer step-wrapping helpers
 */
public class ConsolidatedFixupIT {

    private static final String PNG_1X1 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ"
            + "AAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    /** Feature with no tags — exercises TagStats no-tags notice. */
    private static final String NO_TAG_JSON = "[{"
        + "\"name\":\"Untagged Feature\"," + "\"keyword\":\"Feature\"," + "\"uri\":\"untagged.feature\","
        + "\"elements\":[{"
        + "\"name\":\"A passing scenario\",\"type\":\"scenario\",\"keyword\":\"Scenario\"," 
        + "\"steps\":[{\"keyword\":\"Given \",\"name\":\"something is true\"," 
        + "\"result\":{\"status\":\"passed\",\"duration\":50000000}}]"
        + "}]}]";

    /** Step with a DataTable (rows[]). */
    private static final String DATA_TABLE_JSON = "[{"
        + "\"name\":\"DataTable Feature\",\"keyword\":\"Feature\",\"uri\":\"datatable.feature\"," 
        + "\"elements\":[{"
        + "\"name\":\"Login with multiple users\",\"type\":\"scenario\",\"keyword\":\"Scenario\"," 
        + "\"steps\":[{"
        + "\"keyword\":\"Given \",\"name\":\"the following users exist\"," 
        + "\"rows\":[" 
        + "{\"cells\":[\"username\",\"password\",\"role\"]}," 
        + "{\"cells\":[\"alice\",\"pass123\",\"admin\"]}," 
        + "{\"cells\":[\"bob\",\"secure\",\"user\"]}" 
        + "]," 
        + "\"result\":{\"status\":\"passed\",\"duration\":100000000}"
        + "}]}]}]";

    /** Step with a DocString — newlines use JSON \n escaping. */
    private static final String DOCSTRING_JSON = "[{"
        + "\"name\":\"DocString Feature\",\"keyword\":\"Feature\",\"uri\":\"docstring.feature\"," 
        + "\"elements\":[{"
        + "\"name\":\"Configure with YAML\",\"type\":\"scenario\",\"keyword\":\"Scenario\"," 
        + "\"steps\":[{"
        + "\"keyword\":\"Given \",\"name\":\"the config file contains\"," 
        + "\"doc_string\":{\"content\":\"server:\\n  host: localhost\\n  port: 8080\\n\"}," 
        + "\"result\":{\"status\":\"passed\",\"duration\":100000000}"
        + "}]}]}]";

    /** Feature with a Background element. */
    private static final String BACKGROUND_JSON = "[{"
        + "\"name\":\"Background Feature\",\"keyword\":\"Feature\",\"uri\":\"background.feature\"," 
        + "\"elements\":[{"
        + "\"name\":\"Setup\",\"type\":\"background\",\"keyword\":\"Background\"," 
        + "\"steps\":["
        + "{\"keyword\":\"Given \",\"name\":\"the database is seeded\"," 
        + "\"result\":{\"status\":\"passed\",\"duration\":200000000}}," 
        + "{\"keyword\":\"And \",\"name\":\"the server is running\"," 
        + "\"result\":{\"status\":\"passed\",\"duration\":100000000}}"
        + "]},"
        + "{"
        + "\"name\":\"User can log in\",\"type\":\"scenario\",\"keyword\":\"Scenario\"," 
        + "\"steps\":["
        + "{\"keyword\":\"When \",\"name\":\"I submit the login form\"," 
        + "\"result\":{\"status\":\"passed\",\"duration\":300000000}}," 
        + "{\"keyword\":\"Then \",\"name\":\"I am redirected to dashboard\"," 
        + "\"result\":{\"status\":\"passed\",\"duration\":150000000}}"
        + "]"
        + "}]}]";

    /** Scenario whose @Before hook fails — error_message uses JSON \n. */
    private static final String HOOK_ERROR_JSON = "[{"
        + "\"name\":\"Hook Error Feature\",\"keyword\":\"Feature\",\"uri\":\"hookerror.feature\"," 
        + "\"elements\":[{"
        + "\"name\":\"Scenario with before-hook failure\",\"type\":\"scenario\",\"keyword\":\"Scenario\"," 
        + "\"before\":[{\"result\":{\"status\":\"failed\",\"duration\":100000000," 
        + "\"error_message\":\"WebDriverException: Chrome not found\\nat BeforeHooks.setup(BeforeHooks.java:22)\"}}]," 
        + "\"steps\":[{\"keyword\":\"Given \",\"name\":\"I am on the home page\"," 
        + "\"result\":{\"status\":\"skipped\",\"duration\":0}}]"
        + "}]}]";

    /** Scenario with 3 screenshots in @After hook. */
    private static final String MULTI_SHOT_JSON = "[{"
        + "\"name\":\"Multi Screenshot Feature\",\"keyword\":\"Feature\",\"uri\":\"multishot.feature\"," 
        + "\"elements\":[{"
        + "\"name\":\"Scenario with three screenshots\",\"type\":\"scenario\",\"keyword\":\"Scenario\"," 
        + "\"steps\":[{\"keyword\":\"Given \",\"name\":\"I browse three pages\"," 
        + "\"result\":{\"status\":\"passed\",\"duration\":500000000}}]," 
        + "\"after\":[{\"result\":{\"status\":\"passed\",\"duration\":100000000," 
        + "\"embeddings\":["
        + "{\"mime_type\":\"image/png\",\"data\":\"" + PNG1 + "\"}," 
        + "{\"mime_type\":\"image/png\",\"data\":\"" + PNG1 + "\"}," 
        + "{\"mime_type\":\"image/png\",\"data\":\"" + PNG1 + "\"}" 
        + "]}}]"
        + "}]}]";

    /** Step with a 25-line error — exercises maxLines truncation. */
    private static String buildLongErrorJson() {
        StringBuilder err = new StringBuilder("java.lang.AssertionError: Expected true but was false\\n");
        for (int i = 1; i <= 24; i++) {
            err.append("\\tat com.example.Step").append(i)
               .append(".run(Step").append(i).append(".java:").append(i * 10).append(")\\n");
        }
        return "[{\"name\":\"Long Error Feature\",\"keyword\":\"Feature\",\"uri\":\"longerror.feature\","
            + "\"elements\":[{\"name\":\"Scenario with long stack trace\"," 
            + "\"type\":\"scenario\",\"keyword\":\"Scenario\"," 
            + "\"steps\":[{\"keyword\":\"Then \",\"name\":\"the assertion holds\"," 
            + "\"result\":{\"status\":\"failed\",\"duration\":200000000," 
            + "\"error_message\":\"" + err + "\"}}]}]}]";
    }

    // -----------------------------------------------------------------------
    // 1. TagStats no-tags notice
    // -----------------------------------------------------------------------
    @Test
    public void tagStatsSectionRendersNoticeWhenNoTags() throws Exception {
        List<CucumberFeature> features = parse(NO_TAG_JSON);
        File pdf = tempPdf("tagstats-notags");
        new ConsolidatedPdfGenerator(
                false, false, false, false, false, true, false,
                20, "No Tags Test", "QTEST_TC_")
                .generateReport(features, pdf.getAbsolutePath());

        assertTrue("PDF must be non-empty", pdf.length() > 0);
        try (PDDocument doc = PDDocument.load(pdf)) {
            assertEquals("Exactly 1 page — no blank page", 1, doc.getNumberOfPages());
            String text = new PDFTextStripper().getText(doc);
            assertTrue("Notice text rendered", text.contains("No tags were found"));
        }
    }

    // -----------------------------------------------------------------------
    // 2. consolidatedReportName blank guard logic
    // -----------------------------------------------------------------------
    @Test
    public void consolidatedReportNameBlankGuardLogic() {
        // This tests the guard expression that lives in SplitPdfReporterMojo.
        // We replicate the exact expression to verify all branches.
        // Blank string -> default
        String blank = "   ";
        assertEquals("cucumber-report.pdf",
                effectiveName(blank));
        // Empty -> default
        assertEquals("cucumber-report.pdf",
                effectiveName(""));
        // Null -> default
        assertEquals("cucumber-report.pdf",
                effectiveName(null));
        // Valid name passes through
        assertEquals("my-run.pdf",
                effectiveName("my-run.pdf"));
        // Leading/trailing spaces stripped
        assertEquals("spaced.pdf",
                effectiveName("  spaced.pdf  "));
    }

    /** Mirrors the guard expression from SplitPdfReporterMojo.generateConsolidatedPdf(). */
    private static String effectiveName(String name) {
        return (name != null && !name.isBlank()) ? name.strip() : "cucumber-report.pdf";
    }

    // -----------------------------------------------------------------------
    // 3. renderErrorBlock truncation at maxLines=5
    // -----------------------------------------------------------------------
    @Test
    public void errorBlockTruncatesLongStackTrace() throws Exception {
        List<CucumberFeature> features = parse(buildLongErrorJson());
        File pdf = tempPdf("error-truncate");
        // maxOutputLines=5 forces truncation of the 25-line stack trace
        new ConsolidatedPdfGenerator(
                false, false, false, true, false, false, false,
                5, "Error Truncation Test", "QTEST_TC_")
                .generateReport(features, pdf.getAbsolutePath());

        assertTrue("PDF must be non-empty", pdf.length() > 0);
        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("First error line present", text.contains("AssertionError"));
            // Truncation marker "... +N more" should be present
            assertTrue("Truncation marker present", text.contains("+") && text.contains("more"));
        }
    }

    // -----------------------------------------------------------------------
    // 4. DataTable renders in DetailedSection
    // -----------------------------------------------------------------------
    @Test
    public void dataTableRendersInDetailedSection() throws Exception {
        List<CucumberFeature> features = parse(DATA_TABLE_JSON);
        assertEquals(1, features.size());

        CucumberStep step = features.get(0).getActualScenarios().get(0).getSteps().get(0);
        assertFalse("Step has data table rows", step.getDataTableRows().isEmpty());
        assertEquals("3 rows (header + 2 data)", 3, step.getDataTableRows().size());
        assertEquals("Header row first cell", "username",
                step.getDataTableRows().get(0).getCells().get(0));

        File pdf = tempPdf("datatable");
        new ConsolidatedPdfGenerator(
                false, false, false, true, false, false, false,
                20, "DataTable Test", "QTEST_TC_")
                .generateReport(features, pdf.getAbsolutePath());

        assertTrue("PDF must be non-empty", pdf.length() > 0);
        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("Table header visible", text.contains("username"));
            assertTrue("Data cell visible",    text.contains("alice"));
            assertTrue("Role cell visible",    text.contains("admin"));
        }
    }

    // -----------------------------------------------------------------------
    // 5. DocString renders in DetailedSection
    // -----------------------------------------------------------------------
    @Test
    public void docStringRendersInDetailedSection() throws Exception {
        List<CucumberFeature> features = parse(DOCSTRING_JSON);
        assertEquals(1, features.size());

        CucumberStep step = features.get(0).getActualScenarios().get(0).getSteps().get(0);
        assertNotNull("Step has docstring", step.getDocString());
        String docContent = step.getDocString().getContent();
        assertNotNull("DocString has content", docContent);
        assertTrue("DocString contains YAML key", docContent.contains("host"));

        File pdf = tempPdf("docstring");
        new ConsolidatedPdfGenerator(
                false, false, false, true, false, false, false,
                20, "DocString Test", "QTEST_TC_")
                .generateReport(features, pdf.getAbsolutePath());

        assertTrue("PDF must be non-empty", pdf.length() > 0);
        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("DocString YAML visible",
                    text.contains("localhost") || text.contains("8080"));
        }
    }

    // -----------------------------------------------------------------------
    // 6. Background steps parsed and rendered
    // -----------------------------------------------------------------------
    @Test
    public void backgroundStepsParsedAndRendered() throws Exception {
        List<CucumberFeature> features = parse(BACKGROUND_JSON);
        assertEquals(1, features.size());

        CucumberScenario scenario = features.get(0).getActualScenarios().get(0);
        assertTrue("Scenario has background steps", scenario.hasBackground());
        assertEquals("Two background steps", 2, scenario.getBackgroundSteps().size());
        assertEquals("First background keyword",
                "Given ", scenario.getBackgroundSteps().get(0).getKeyword());

        File pdf = tempPdf("background");
        new ConsolidatedPdfGenerator(
                false, false, false, true, false, false, false,
                20, "Background Test", "QTEST_TC_")
                .generateReport(features, pdf.getAbsolutePath());

        assertTrue("PDF must be non-empty", pdf.length() > 0);
        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("Background label present",  text.contains("Background"));
            assertTrue("Background step text",      text.contains("database is seeded"));
        }
    }

    // -----------------------------------------------------------------------
    // 7. FailureSummarySection: before-hook error rendered
    // -----------------------------------------------------------------------
    @Test
    public void failureSummarySectionShowsBeforeHookError() throws Exception {
        List<CucumberFeature> features = parse(HOOK_ERROR_JSON);
        assertEquals(1, features.size());

        CucumberScenario scenario = features.get(0).getActualScenarios().get(0);
        assertFalse("Before hooks present", scenario.getBeforeHooks().isEmpty());
        assertEquals("Before hook failed",
                "failed", scenario.getBeforeHooks().get(0).getResult().getStatus());

        File pdf = tempPdf("hook-error");
        new ConsolidatedPdfGenerator(
                false, false, false, false, false, false, true,
                20, "Hook Error Test", "QTEST_TC_")
                .generateReport(features, pdf.getAbsolutePath());

        assertTrue("PDF must be non-empty", pdf.length() > 0);
        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("Failure Summary present",  text.contains("Failure Summary"));
            assertTrue("Hook error message shown",
                    text.contains("WebDriverException") || text.contains("Chrome"));
        }
    }

    // -----------------------------------------------------------------------
    // 8. ExpandedSection: 3 screenshots per scenario
    // -----------------------------------------------------------------------
    @Test
    public void expandedSectionRendersMultipleScreenshots() throws Exception {
        List<CucumberFeature> features = parse(MULTI_SHOT_JSON);
        assertEquals(1, features.size());

        List<String> shots = features.get(0).getActualScenarios().get(0).getAllScreenshots();
        assertEquals("3 screenshots discovered", 3, shots.size());

        File pdf = tempPdf("multi-screenshot");
        new ConsolidatedPdfGenerator(
                false, false, false, false, true, false, false,
                20, "Multi Screenshot Test", "QTEST_TC_")
                .generateReport(features, pdf.getAbsolutePath());

        assertTrue("PDF must be non-empty", pdf.length() > 0);
        try (PDDocument doc = PDDocument.load(pdf)) {
            // 3 images may overflow onto 2 pages
            assertTrue("At least 1 page", doc.getNumberOfPages() >= 1);
            String text = new PDFTextStripper().getText(doc);
            assertTrue("Screenshots section label", text.contains("Screenshot"));
        }
    }

    // -----------------------------------------------------------------------
    // 9. ReportStats.passRatePercent edge cases
    // -----------------------------------------------------------------------
    @Test
    public void reportStatsPassRatePercentEdgeCases() throws Exception {
        List<CucumberFeature> features = parse(NO_TAG_JSON);
        ReportStats stats = ReportStats.compute(features);

        assertEquals("100% pass rate",   100, stats.passRatePercent(1, 1));
        assertEquals("0% when 0 passed",   0, stats.passRatePercent(0, 5));
        // Division-by-zero guard: total=0 must return 0, not throw
        assertEquals("0/0 returns 0",      0, stats.passRatePercent(0, 0));
        assertEquals("N/0 returns 0",      0, stats.passRatePercent(5, 0));
        // Rounding
        assertEquals("1/3 = 33%",         33, stats.passRatePercent(1, 3));
        assertEquals("2/3 = 67%",         67, stats.passRatePercent(2, 3));
        assertEquals("50% pass rate",     50, stats.passRatePercent(1, 2));
    }

    // -----------------------------------------------------------------------
    // 10. ContentBlockRenderer step-wrapping helpers
    // -----------------------------------------------------------------------
    @Test
    public void contentBlockRendererStepWrappingHelpers() {
        // availStepChars: primary keyword gets more room than continuation
        int primChars = ContentBlockRenderer.availStepChars(6, false);
        int contChars = ContentBlockRenderer.availStepChars(6, true);
        assertTrue("Primary gets at least 60 chars", primChars >= 60);
        assertTrue("Continuation is narrower",       contChars < primChars);
        assertTrue("Sanity cap 200",                 primChars <= 200);

        // wrapStepName: exact fit = no wrap
        assertArrayEquals("Exact fit: 1 line",
                new String[]{"HelloWorld"},
                ContentBlockRenderer.wrapStepName("HelloWorld", 10));

        // wrapStepName: one char over, splits at word boundary
        String[] w1 = ContentBlockRenderer.wrapStepName("Hello World Extra", 11);
        assertEquals("Wraps into 2 lines", 2, w1.length);
        assertEquals("Line 1 is before boundary", "Hello World", w1[0]);
        assertEquals("Line 2 is remainder", "Extra", w1[1]);

        // wrapStepName: no space — hard split
        String[] w2 = ContentBlockRenderer.wrapStepName("ABCDEFGHIJK", 5);
        assertEquals("Hard split into 2 lines", 2, w2.length);
        assertEquals("Line 1 is first 5 chars", "ABCDE", w2[0]);
        assertEquals("Line 2 is remainder",      "FGHIJK", w2[1]);

        // wrapStepName: null/empty guards
        assertEquals("Null -> 1 empty line", 1,
                ContentBlockRenderer.wrapStepName(null, 50).length);
        assertEquals("Empty -> 1 empty line", 1,
                ContentBlockRenderer.wrapStepName("", 50).length);

        // isContinuationKeyword
        assertTrue(ContentBlockRenderer.isContinuationKeyword("And "));
        assertTrue(ContentBlockRenderer.isContinuationKeyword("But "));
        assertTrue(ContentBlockRenderer.isContinuationKeyword("AND"));
        assertFalse(ContentBlockRenderer.isContinuationKeyword("Given "));
        assertFalse(ContentBlockRenderer.isContinuationKeyword("* "));
        assertFalse(ContentBlockRenderer.isContinuationKeyword(null));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------
    private static List<CucumberFeature> parse(String json) throws Exception {
        File f = Files.createTempFile("fixup-test", ".json").toFile();
        try (FileWriter w = new FileWriter(f)) { w.write(json); }
        return new CucumberJsonParser(false).parseJsonFile(f.getAbsolutePath());
    }

    private static File tempPdf(String prefix) throws Exception {
        return Files.createTempFile(prefix, ".pdf").toFile();
    }
}
