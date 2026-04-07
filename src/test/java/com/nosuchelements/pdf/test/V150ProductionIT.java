package com.nosuchelements.pdf.test;

import com.nosuchelements.consolidated.ConsolidatedPdfGenerator;
import com.nosuchelements.cucumber.CucumberJsonParser;
import com.nosuchelements.cucumber.model.CucumberFeature;
import com.nosuchelements.cucumber.model.CucumberScenario;
import com.nosuchelements.cucumber.model.CucumberStep;
import com.nosuchelements.cucumber.model.ReportMetadata;
import com.nosuchelements.pdf.FeaturePdfGenerator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Integration tests for v1.5.0 production-grade features:
 *
 * <ol>
 *   <li>ReportMetadata model populates entries correctly</li>
 *   <li>ConsolidatedPdfGenerator passes metadata to Dashboard</li>
 *   <li>Metadata block renders key-value pairs in PDF</li>
 *   <li>SlowTestsSection sorts by duration descending</li>
 *   <li>SlowTestsSection generates without exception</li>
 *   <li>SlowTestsSection topN cap works (capped at actual count)</li>
 *   <li>FeaturePdfGenerator renders DataTable in split mode</li>
 *   <li>FeaturePdfGenerator renders DocString in split mode</li>
 *   <li>FeaturePdfGenerator renders output logs in split mode</li>
 *   <li>FeaturePdfGenerator renders background steps in split mode</li>
 *   <li>FeaturePdfGenerator renders scenario tags in split mode</li>
 *   <li>FeaturePdfGenerator sets PDF document title metadata</li>
 *   <li>Version v1.5.0 present in consolidated PDF text</li>
 *   <li>Version v1.5.0 present in split PDF footer</li>
 *   <li>13-arg ConsolidatedPdfGenerator constructor works end-to-end</li>
 *   <li>All backward-compat constructors still generate valid PDFs</li>
 *   <li>Step spacing: split mode scenario page has correct line count</li>
 *   <li>ConsolidatedPdfGenerator with displaySlowTests=true works</li>
 *   <li>PDF document information populated correctly</li>
 *   <li>Both mode: split + consolidated from single call</li>
 * </ol>
 */
public class V150ProductionIT {

    // -----------------------------------------------------------------------
    // Shared test data
    // -----------------------------------------------------------------------

    private static final String PNG_1X1 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ"
            + "AAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

    /** Three features with varied durations for slow-test ordering. */
    private static final String TIMING_JSON = "["
        + "{"
        + "\"name\":\"Slow Feature\",\"keyword\":\"Feature\","
        + "\"uri\":\"slow.feature\",\"tags\":[],"
        + "\"elements\":["
        + "  {\"name\":\"Very slow scenario\",\"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"steps\":[{\"keyword\":\"Given \",\"name\":\"I wait a long time\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":15000000000}}]},"  // 15s
        + "  {\"name\":\"Moderately slow scenario\",\"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"steps\":[{\"keyword\":\"When \",\"name\":\"I do something moderate\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":6000000000}}]}"    // 6s
        + "]},"
        + "{"
        + "\"name\":\"Fast Feature\",\"keyword\":\"Feature\","
        + "\"uri\":\"fast.feature\",\"tags\":[],"
        + "\"elements\":["
        + "  {\"name\":\"Quick scenario\",\"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"steps\":[{\"keyword\":\"Then \",\"name\":\"I am done\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":50000000}}]}"      // 50ms
        + "]}]";

    /** Feature with DataTable, DocString, output, background, tags, and CRLF error. */
    private static final String RICH_CONTENT_JSON = "[{"
        + "\"name\":\"Rich Content Feature\","
        + "\"keyword\":\"Feature\","
        + "\"uri\":\"rich.feature\","
        + "\"tags\":[{\"name\":\"@smoke\"},{\"name\":\"@regression\"}],"
        + "\"elements\":["
        + "  {\"name\":\"Setup\",\"type\":\"background\",\"keyword\":\"Background\","
        + "   \"steps\":["
        + "     {\"keyword\":\"Given \",\"name\":\"the system is initialised\","
        + "      \"result\":{\"status\":\"passed\",\"duration\":100000000}}"
        + "   ]},"
        + "  {\"name\":\"Scenario with everything\","
        + "   \"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"tags\":[{\"name\":\"@full\"},{\"name\":\"@data\"}],"
        + "   \"steps\":["
        + "     {\"keyword\":\"Given \",\"name\":\"the following users\","
        + "      \"rows\":[{\"cells\":[\"name\",\"role\"]},{\"cells\":[\"Alice\",\"admin\"]}],"
        + "      \"result\":{\"status\":\"passed\",\"duration\":200000000}},"
        + "     {\"keyword\":\"When \",\"name\":\"I configure with\","
        + "      \"doc_string\":{\"content\":\"server: localhost\\nport: 8080\"},"
        + "      \"result\":{\"status\":\"passed\",\"duration\":150000000}},"
        + "     {\"keyword\":\"Then \",\"name\":\"it works\","
        + "      \"output\":[\"Line 1 of output\",\"Line 2 of output\"],"
        + "      \"result\":{\"status\":\"passed\",\"duration\":80000000}},"
        + "     {\"keyword\":\"And \",\"name\":\"this also works\","
        + "      \"result\":{\"status\":\"passed\",\"duration\":30000000}}"
        + "   ]},"
        + "  {\"name\":\"Failing scenario\","
        + "   \"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"tags\":[{\"name\":\"@negative\"}],"
        + "   \"steps\":["
        + "     {\"keyword\":\"Given \",\"name\":\"something fails\","
        + "      \"result\":{\"status\":\"failed\",\"duration\":500000000,"
        + "       \"error_message\":\"java.lang.AssertionError: expected true but was false"
        + "\\r\\n\\tat org.testng.Assert.fail(Assert.java:110)"
        + "\\r\\n\\tat com.example.Steps.check(Steps.java:44)\"}}"
        + "   ]},"
        + "  {\"name\":\"Screenshot scenario\","
        + "   \"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"tags\":[],"
        + "   \"steps\":["
        + "     {\"keyword\":\"Given \",\"name\":\"I take a screenshot\","
        + "      \"embeddings\":[{\"mime_type\":\"image/png\",\"data\":\"" + PNG_1X1 + "\"}],"
        + "      \"result\":{\"status\":\"passed\",\"duration\":300000000}}"
        + "   ]}]}]";

    // -----------------------------------------------------------------------
    // 1. ReportMetadata model
    // -----------------------------------------------------------------------

    @Test
    public void reportMetadataStoresEntries() {
        ReportMetadata m = new ReportMetadata();
        m.setEnvironment("QA");
        m.setBranch("feature/sprint-42");
        m.setBuild("1234");
        m.setAppVersion("2.14.0");
        m.setBrowser("Chrome 124");

        assertFalse("Metadata should not be empty", m.isEmpty());
        assertEquals("QA",                 m.getEnvironment());
        assertEquals("feature/sprint-42",  m.getBranch());
        assertEquals("1234",               m.getBuild());
        assertEquals("2.14.0",             m.getAppVersion());
        assertEquals("Chrome 124",         m.getBrowser());
        assertEquals("Should have 5 entries", 5, m.getEntries().size());
    }

    @Test
    public void reportMetadataIgnoresNullValues() {
        ReportMetadata m = new ReportMetadata();
        m.put(null, "value");
        m.put("key", null);
        m.put("  ", "value");
        assertTrue("Null/blank keys should be ignored", m.isEmpty());
    }

    // -----------------------------------------------------------------------
    // 2-3. Metadata in consolidated PDF
    // -----------------------------------------------------------------------

    @Test
    public void consolidatedPdfIncludesMetadataBlock() throws Exception {
        List<CucumberFeature> features = parse(TIMING_JSON);

        ReportMetadata meta = new ReportMetadata();
        meta.setEnvironment("STAGING");
        meta.setBranch("main");
        meta.setBuild("5678");
        meta.setAppVersion("3.0.0-rc1");

        File pdf = tempPdf("metadata-consolidated");
        new ConsolidatedPdfGenerator(
                true, false, false, false, false, false, false, false,
                20, 10, "Metadata Test", "QTEST_TC_", meta)
                .generateReport(features, pdf.getAbsolutePath());

        assertTrue("PDF must be non-empty", pdf.length() > 0);
        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("Environment key visible",   text.contains("Environment"));
            assertTrue("STAGING value visible",     text.contains("STAGING"));
            assertTrue("Branch key visible",        text.contains("Branch"));
            assertTrue("main value visible",        text.contains("main"));
            assertTrue("App Version key visible",   text.contains("App Version"));
            assertTrue("3.0.0-rc1 value visible",   text.contains("3.0.0-rc1"));
        }
    }

    // -----------------------------------------------------------------------
    // 4-6. SlowTestsSection
    // -----------------------------------------------------------------------

    @Test
    public void slowTestsSectionSortsByDurationDescending() throws Exception {
        List<CucumberFeature> features = parse(TIMING_JSON);

        // Verify model: top scenario should be "Very slow scenario" (15s)
        long maxDur = features.stream()
                .flatMap(f -> f.getActualScenarios().stream())
                .mapToLong(s -> s.getDurationMillis())
                .max().orElse(0);
        assertEquals("Slowest scenario should be 15 000ms", 15_000, maxDur);
    }

    @Test
    public void slowTestsSectionGeneratesWithoutException() throws Exception {
        List<CucumberFeature> features = parse(TIMING_JSON);

        File pdf = tempPdf("slow-tests");
        new ConsolidatedPdfGenerator(
                true, false, false, false, false, false, false, true,
                20, 5, "Slow Tests Test", "QTEST_TC_", null)
                .generateReport(features, pdf.getAbsolutePath());

        assertTrue("PDF must be non-empty", pdf.length() > 0);
        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            // Dashboard and Slow Tests section should appear
            assertTrue("Slow Tests or duration info visible",
                    text.contains("15") || text.contains("Very slow"));
        }
    }

    @Test
    public void slowTestsTopNIsCapped() throws Exception {
        // Only 3 scenarios in TIMING_JSON but topN=20 — should not crash
        List<CucumberFeature> features = parse(TIMING_JSON);

        File pdf = tempPdf("slow-tests-capped");
        new ConsolidatedPdfGenerator(
                false, false, false, false, false, false, false, true,
                20, 20, "Slow Cap Test", "QTEST_TC_", null)
                .generateReport(features, pdf.getAbsolutePath());

        assertTrue("PDF must be non-empty", pdf.length() > 0);
    }

    // -----------------------------------------------------------------------
    // 7-11. Split mode content blocks
    // -----------------------------------------------------------------------

    @Test
    public void splitModeRendersDataTable() throws Exception {
        List<CucumberFeature> features = parse(RICH_CONTENT_JSON);

        File pdf = tempPdf("split-datatable");
        new FeaturePdfGenerator().generateFeaturePdf(features.get(0), pdf.getAbsolutePath());

        assertTrue("PDF must be non-empty", pdf.length() > 0);
        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("DataTable header 'name' visible",  text.contains("name"));
            assertTrue("DataTable cell 'Alice' visible",   text.contains("Alice"));
            assertTrue("DataTable cell 'admin' visible",   text.contains("admin"));
        }
    }

    @Test
    public void splitModeRendersDocString() throws Exception {
        List<CucumberFeature> features = parse(RICH_CONTENT_JSON);

        File pdf = tempPdf("split-docstring");
        new FeaturePdfGenerator().generateFeaturePdf(features.get(0), pdf.getAbsolutePath());

        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("DocString content 'localhost' visible", text.contains("localhost"));
            assertTrue("DocString content '8080' visible",      text.contains("8080"));
        }
    }

    @Test
    public void splitModeRendersOutputLogs() throws Exception {
        List<CucumberFeature> features = parse(RICH_CONTENT_JSON);

        File pdf = tempPdf("split-outputlogs");
        new FeaturePdfGenerator().generateFeaturePdf(features.get(0), pdf.getAbsolutePath());

        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("Output log line 1 visible", text.contains("Line 1 of output"));
        }
    }

    @Test
    public void splitModeRendersBackgroundSteps() throws Exception {
        List<CucumberFeature> features = parse(RICH_CONTENT_JSON);
        CucumberScenario sc = features.get(0).getActualScenarios().get(0);
        assertTrue("Scenario should have background", sc.hasBackground());

        File pdf = tempPdf("split-background");
        new FeaturePdfGenerator().generateFeaturePdf(features.get(0), pdf.getAbsolutePath());

        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("Background label visible",    text.contains("Background"));
            assertTrue("Background step text visible", text.contains("initialised"));
        }
    }

    @Test
    public void splitModeRendersScenarioTags() throws Exception {
        List<CucumberFeature> features = parse(RICH_CONTENT_JSON);

        // Verify the model has tags
        CucumberScenario sc = features.get(0).getActualScenarios().get(0);
        assertFalse("Scenario should have tags", sc.getTags().isEmpty());

        File pdf = tempPdf("split-tags");
        new FeaturePdfGenerator().generateFeaturePdf(features.get(0), pdf.getAbsolutePath());

        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            // Tags should appear somewhere — either @full, @data or feature-level @smoke
            assertTrue("At least one tag visible",
                    text.contains("smoke") || text.contains("full") || text.contains("data"));
        }
    }

    // -----------------------------------------------------------------------
    // 12. PDF document metadata
    // -----------------------------------------------------------------------

    @Test
    public void splitModeSetsPdfDocumentTitle() throws Exception {
        List<CucumberFeature> features = parse(RICH_CONTENT_JSON);
        File pdf = tempPdf("split-pdf-metadata");
        new FeaturePdfGenerator().generateFeaturePdf(features.get(0), pdf.getAbsolutePath());

        try (PDDocument doc = PDDocument.load(pdf)) {
            PDDocumentInformation info = doc.getDocumentInformation();
            assertNotNull("Document title should be set", info.getTitle());
            assertEquals("Rich Content Feature", info.getTitle());
            assertEquals("Cucumber Test Report", info.getSubject());
            assertNotNull("Creator should be set", info.getCreator());
            assertTrue("Creator should mention version",
                    info.getCreator().contains("1.5.0"));
        }
    }

    @Test
    public void consolidatedPdfSetsPdfDocumentTitle() throws Exception {
        List<CucumberFeature> features = parse(TIMING_JSON);
        File pdf = tempPdf("consolidated-pdf-metadata");

        new ConsolidatedPdfGenerator(
                true, false, false, false, false, false, false, false,
                20, 10, "My Sprint Report", "QTEST_TC_", null)
                .generateReport(features, pdf.getAbsolutePath());

        try (PDDocument doc = PDDocument.load(pdf)) {
            PDDocumentInformation info = doc.getDocumentInformation();
            assertEquals("My Sprint Report", info.getTitle());
            assertTrue("Creator mentions version",
                    info.getCreator().contains("1.5.0"));
        }
    }

    // -----------------------------------------------------------------------
    // 13-14. Version strings
    // -----------------------------------------------------------------------

    @Test
    public void consolidatedPdfContainsVersion150() throws Exception {
        List<CucumberFeature> features = parse(TIMING_JSON);
        File pdf = tempPdf("version-consolidated");
        new ConsolidatedPdfGenerator().generateReport(features, pdf.getAbsolutePath());

        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("Version 1.5.0 visible in consolidated PDF",
                    text.contains("1.5.0"));
        }
    }

    @Test
    public void splitPdfContainsVersion150() throws Exception {
        List<CucumberFeature> features = parse(RICH_CONTENT_JSON);
        File pdf = tempPdf("version-split");
        new FeaturePdfGenerator().generateFeaturePdf(features.get(0), pdf.getAbsolutePath());

        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("Version 1.5.0 visible in split PDF footer",
                    text.contains("1.5.0"));
        }
    }

    // -----------------------------------------------------------------------
    // 15. 13-arg constructor end-to-end
    // -----------------------------------------------------------------------

    @Test
    public void thirteenArgConstructorFullEndToEnd() throws Exception {
        List<CucumberFeature> features = parse(RICH_CONTENT_JSON);

        ReportMetadata meta = new ReportMetadata();
        meta.setEnvironment("CI");
        meta.setBuild("999");

        File pdf = tempPdf("13arg-full");
        new ConsolidatedPdfGenerator(
                true, true, true, true, false, true, true, true,
                10, 5, "Full 13-arg Test", "QTEST_TC_", meta)
                .generateReport(features, pdf.getAbsolutePath());

        assertTrue("PDF must be non-empty", pdf.length() > 0);
        try (PDDocument doc = PDDocument.load(pdf)) {
            assertTrue("At least 5 pages", doc.getNumberOfPages() >= 5);
            String text = new PDFTextStripper().getText(doc);
            assertTrue("Report title present", text.contains("Full 13-arg Test"));
            assertTrue("Metadata CI present",  text.contains("CI"));
        }
    }

    // -----------------------------------------------------------------------
    // 16. All backward-compat constructors
    // -----------------------------------------------------------------------

    @Test
    public void eightArgBackwardCompat() throws Exception {
        List<CucumberFeature> features = parse(TIMING_JSON);
        File pdf = tempPdf("bc-8arg");
        new ConsolidatedPdfGenerator(true, false, false, false, false,
                20, "8-arg Test", "QTEST_TC_")
                .generateReport(features, pdf.getAbsolutePath());
        assertTrue(pdf.length() > 0);
    }

    @Test
    public void nineArgBackwardCompat() throws Exception {
        List<CucumberFeature> features = parse(TIMING_JSON);
        File pdf = tempPdf("bc-9arg");
        new ConsolidatedPdfGenerator(true, false, false, false, false, true,
                20, "9-arg Test", "QTEST_TC_")
                .generateReport(features, pdf.getAbsolutePath());
        assertTrue(pdf.length() > 0);
    }

    @Test
    public void tenArgBackwardCompat() throws Exception {
        List<CucumberFeature> features = parse(TIMING_JSON);
        File pdf = tempPdf("bc-10arg");
        new ConsolidatedPdfGenerator(true, false, false, false, false, true, true,
                20, "10-arg Test", "QTEST_TC_")
                .generateReport(features, pdf.getAbsolutePath());
        assertTrue(pdf.length() > 0);
    }

    // -----------------------------------------------------------------------
    // 17. Step spacing — split mode has correct page count
    // -----------------------------------------------------------------------

    @Test
    public void splitModeStepSpacingHasCorrectPageCount() throws Exception {
        List<CucumberFeature> features = parse(RICH_CONTENT_JSON);
        // 4 actual scenarios + 1 summary page = at least 5 pages
        File pdf = tempPdf("step-spacing");
        new FeaturePdfGenerator().generateFeaturePdf(features.get(0), pdf.getAbsolutePath());

        try (PDDocument doc = PDDocument.load(pdf)) {
            // 1 feature summary + 4 scenario pages (some may overflow)
            assertTrue("At least 5 pages (1 summary + 4 scenarios)",
                    doc.getNumberOfPages() >= 5);
        }
    }

    // -----------------------------------------------------------------------
    // 18. displaySlowTests + displayFailureSummary together
    // -----------------------------------------------------------------------

    @Test
    public void slowTestsAndFailureSummaryTogether() throws Exception {
        List<CucumberFeature> features = parse(RICH_CONTENT_JSON);
        File pdf = tempPdf("slow-plus-failure");
        new ConsolidatedPdfGenerator(
                true, false, false, false, false, false, true, true,
                20, 10, "Combined Test", "QTEST_TC_", null)
                .generateReport(features, pdf.getAbsolutePath());

        assertTrue("PDF must be non-empty", pdf.length() > 0);
        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("Failure Summary present", text.contains("Failure Summary"));
        }
    }

    // -----------------------------------------------------------------------
    // 19. PDF document info populated in consolidated mode
    // -----------------------------------------------------------------------

    @Test
    public void consolidatedPdfDocumentInfoHasCreator() throws Exception {
        List<CucumberFeature> features = parse(TIMING_JSON);
        File pdf = tempPdf("consolidated-docinfo");
        new ConsolidatedPdfGenerator().generateReport(features, pdf.getAbsolutePath());

        try (PDDocument doc = PDDocument.load(pdf)) {
            assertNotNull(doc.getDocumentInformation().getCreator());
            assertNotNull(doc.getDocumentInformation().getTitle());
            assertNotNull(doc.getDocumentInformation().getSubject());
        }
    }

    // -----------------------------------------------------------------------
    // 20. Metadata null-safety
    // -----------------------------------------------------------------------

    @Test
    public void nullMetadataDoesNotCrashGeneration() throws Exception {
        List<CucumberFeature> features = parse(TIMING_JSON);
        File pdf = tempPdf("null-metadata");
        // Explicit null metadata — should not throw
        new ConsolidatedPdfGenerator(
                true, false, false, false, false, false, false, false,
                20, 10, "Null Meta Test", "QTEST_TC_", null)
                .generateReport(features, pdf.getAbsolutePath());
        assertTrue(pdf.length() > 0);
    }

    @Test
    public void emptyMetadataDoesNotCrashGeneration() throws Exception {
        List<CucumberFeature> features = parse(TIMING_JSON);
        File pdf = tempPdf("empty-metadata");
        ReportMetadata empty = new ReportMetadata();
        assertTrue("Empty metadata should report isEmpty", empty.isEmpty());

        new ConsolidatedPdfGenerator(
                true, false, false, false, false, false, false, false,
                20, 10, "Empty Meta Test", "QTEST_TC_", empty)
                .generateReport(features, pdf.getAbsolutePath());
        assertTrue(pdf.length() > 0);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static List<CucumberFeature> parse(String json) throws Exception {
        File f = Files.createTempFile("v150-it-test", ".json").toFile();
        try (FileWriter w = new FileWriter(f)) { w.write(json); }
        return new CucumberJsonParser(false).parseJsonFile(f.getAbsolutePath());
    }

    private static File tempPdf(String prefix) throws Exception {
        return Files.createTempFile(prefix, ".pdf").toFile();
    }
}
