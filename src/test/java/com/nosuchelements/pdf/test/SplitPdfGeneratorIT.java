package com.nosuchelements.pdf.test;

import com.nosuchelements.cucumber.CucumberJsonParser;
import com.nosuchelements.cucumber.model.CucumberFeature;
import com.nosuchelements.cucumber.model.CucumberScenario;
import com.nosuchelements.cucumber.model.CucumberStep;
import com.nosuchelements.pdf.FeaturePdfGenerator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Integration tests for {@link FeaturePdfGenerator} (split mode).
 *
 * <h3>Coverage targets</h3>
 * <ol>
 *   <li><strong>Bug 1 — CRLF / multi-line stack trace does not crash split mode</strong>
 *       <br>The {@code exceptions.feature} JSON contains error messages with
 *       {@code \r\n} line endings and many {@code \t}-prefixed Java stack frames.
 *       The original generator held a single PDPageContentStream open for the
 *       whole scenario and a stub page-overflow that only clamped Y — causing
 *       PDFBox to write at negative coordinates and throw.  These tests assert
 *       that generation completes without exception and produces a valid,
 *       non-empty PDF.</li>
 *   <li><strong>Bug 2 — Step and hook embeddings (screenshots) are rendered</strong>
 *       <br>The parser correctly populates {@code step.getEmbeddings()} but
 *       the original split-mode renderer never called it.  These tests verify
 *       that PDFs with embedded images are larger than PDFs without, and that
 *       generation does not throw.</li>
 *   <li><strong>Multi-page overflow</strong> — a scenario with enough steps and
 *       a long stack trace must be rendered across multiple pages.</li>
 *   <li><strong>Before-hook screenshot captured by {@code getAllScreenshots()}</strong>
 *       — verifies that the model fix in {@link CucumberScenario} is effective.</li>
 * </ol>
 */
public class SplitPdfGeneratorIT {

    // -----------------------------------------------------------------------
    // Minimal 1×1 transparent PNG, base64-encoded (valid PDFBox input)
    // -----------------------------------------------------------------------
    private static final String PNG_1X1 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ"
            + "AAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

    // -----------------------------------------------------------------------
    // JSON fixtures
    // -----------------------------------------------------------------------

    /**
     * Mirrors the real {@code exceptions.feature} pattern from the uploaded
     * Cucumber.json: CRLF line endings ({@code \r\n}) and tab-prefixed Java
     * stack frames ({@code \t\tat ...}).  This is the exact pattern that
     * triggered Bug 1.
     */
    private static final String EXCEPTION_FEATURE_JSON = "[{"
        + "\"name\":\"Exception\","
        + "\"keyword\":\"Feature\","
        + "\"uri\":\"file:src/test/resources/features/exceptions.feature\","
        + "\"tags\":[],"
        + "\"elements\":["
        + "  {\"name\":\"Exception\",\"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"steps\":[{"
        + "     \"keyword\":\"Given \",\"name\":\"Raise exception\","
        + "     \"result\":{\"status\":\"failed\",\"duration\":15031100,"
        + "       \"error_message\":\"java.lang.AssertionError: expected [true] but found [false]"
          // CRLF line endings — the exact format produced by TestNG/JUnit on Windows
        + "\\r\\n\\tat org.testng.Assert.fail(Assert.java:110)"
        + "\\r\\n\\tat org.testng.Assert.failNotEquals(Assert.java:1413)"
        + "\\r\\n\\tat org.testng.Assert.assertEqualsImpl(Assert.java:149)"
        + "\\r\\n\\tat org.testng.Assert.assertEquals(Assert.java:131)"
        + "\\r\\n\\tat org.testng.Assert.assertTrue(Assert.java:56)"
        + "\\r\\n\\tat com.example.StepDefs.raiseException(StepDefs.java:44)"
        + "\\r\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)"
        + "\\r\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)"
        + "\\r\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)"
        + "\\r\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\""
        + "  }}]},"
        + "  {\"name\":\"No Exception\",\"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"steps\":[{"
        + "     \"keyword\":\"Given \",\"name\":\"Do not raise exception\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":0}"
        + "   }]},"
        + "  {\"name\":\"Other Exception\",\"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"steps\":[{"
        + "     \"keyword\":\"Given \",\"name\":\"Raise exception\","
        + "     \"result\":{\"status\":\"failed\",\"duration\":12000000,"
        + "       \"error_message\":\"java.lang.AssertionError: expected [true] but found [false]"
        + "\\r\\n\\tat org.testng.Assert.fail(Assert.java:110)"
        + "\\r\\n\\tat org.testng.Assert.failNotEquals(Assert.java:1413)\"}"
        + "   },{"
        + "     \"keyword\":\"And \",\"name\":\"Raise exception\","
        + "     \"result\":{\"status\":\"skipped\",\"duration\":0}"
        + "   }]},"
        + "  {\"name\":\"Check exception 1\",\"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"steps\":[{"
        + "     \"keyword\":\"Given \",\"name\":\"Raise exception\","
        + "     \"result\":{\"status\":\"failed\",\"duration\":11500000,"
        + "       \"error_message\":\"java.lang.AssertionError: expected [true] but found [false]"
        + "\\r\\n\\tat org.testng.Assert.fail(Assert.java:110)\"}"
        + "   }]},"
        + "  {\"name\":\"Check exception 2\",\"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"steps\":[{"
        + "     \"keyword\":\"Given \",\"name\":\"Raise exception\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":500000}"
        + "   }]}"
        + "]}]";

    /** Feature with step-level embeddings — mirrors the TwoScreenshots feature. */
    private static final String STEP_SCREENSHOT_JSON = "[{"
        + "\"name\":\"Two Screenshots\","
        + "\"keyword\":\"Feature\","
        + "\"uri\":\"file:src/test/resources/features/twoimages.feature\","
        + "\"tags\":[],"
        + "\"elements\":["
        + "  {\"name\":\"Scenario screenshot 2\",\"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"steps\":[{"
        + "     \"keyword\":\"Given \",\"name\":\"Go to capture 2 images in one step\","
        + "     \"embeddings\":["
        + "       {\"mime_type\":\"image/png\",\"data\":\"" + PNG_1X1 + "\"},"
        + "       {\"mime_type\":\"image/png\",\"data\":\"" + PNG_1X1 + "\"}"
        + "     ],"
        + "     \"result\":{\"status\":\"passed\",\"duration\":2000000000}"
        + "   }]"
        + "  }]"
        + "}]";

    /** Feature with after-hook embedding. */
    private static final String AFTER_HOOK_SCREENSHOT_JSON = "[{"
        + "\"name\":\"Hook Screenshot Feature\","
        + "\"keyword\":\"Feature\","
        + "\"uri\":\"hookshot.feature\","
        + "\"elements\":["
        + "  {\"name\":\"Scenario with after-hook screenshot\","
        + "   \"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"steps\":[{"
        + "     \"keyword\":\"Given \",\"name\":\"I navigate to a page\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":500000000}"
        + "   }],"
        + "   \"after\":[{"
        + "     \"result\":{\"status\":\"passed\",\"duration\":50000000},"
        + "     \"embeddings\":[{\"mime_type\":\"image/png\",\"data\":\"" + PNG_1X1 + "\"}]"
        + "   }]"
        + "  }]"
        + "}]";

    /** Feature with before-hook embedding — tests the getAllScreenshots() fix. */
    private static final String BEFORE_HOOK_SCREENSHOT_JSON = "[{"
        + "\"name\":\"Before Hook Screenshot\","
        + "\"keyword\":\"Feature\","
        + "\"uri\":\"beforehookshot.feature\","
        + "\"elements\":["
        + "  {\"name\":\"Scenario with before-hook screenshot\","
        + "   \"type\":\"scenario\",\"keyword\":\"Scenario\","
        + "   \"before\":[{"
        + "     \"result\":{\"status\":\"passed\",\"duration\":30000000},"
        + "     \"embeddings\":[{\"mime_type\":\"image/png\",\"data\":\"" + PNG_1X1 + "\"}]"
        + "   }],"
        + "   \"steps\":[{"
        + "     \"keyword\":\"Given \",\"name\":\"I am on the home page\","
        + "     \"result\":{\"status\":\"passed\",\"duration\":200000000}"
        + "   }]"
        + "  }]"
        + "}]";

    /** Feature with a very long stack trace — forces multi-page overflow. */
    private static String buildLongStackTraceJson() {
        StringBuilder err = new StringBuilder(
                "java.lang.RuntimeException: Database connection refused\\r\\n");
        for (int i = 0; i < 40; i++) {
            err.append("\\tat com.example.db.Pool.acquire(Pool.java:").append(i * 10 + 1)
               .append(")\\r\\n");
        }
        return "[{\"name\":\"Long Stack Trace Feature\","
            + "\"keyword\":\"Feature\","
            + "\"uri\":\"longstack.feature\","
            + "\"elements\":["
            + "  {\"name\":\"Scenario with a very deep stack trace\","
            + "   \"type\":\"scenario\",\"keyword\":\"Scenario\","
            + "   \"steps\":["
            + "    {\"keyword\":\"Given \",\"name\":\"I connect to the database\","
            + "     \"result\":{\"status\":\"failed\",\"duration\":5000000,"
            + "       \"error_message\":\"" + err + "\"}},"
            + "    {\"keyword\":\"When \",\"name\":\"I execute a query\","
            + "     \"result\":{\"status\":\"skipped\",\"duration\":0}},"
            + "    {\"keyword\":\"Then \",\"name\":\"I see results\","
            + "     \"result\":{\"status\":\"skipped\",\"duration\":0}}"
            + "  ]}]}]";
    }

    // -----------------------------------------------------------------------
    // Bug 1 tests — exceptions.feature crash
    // -----------------------------------------------------------------------

    /**
     * Bug 1 regression: generating a split PDF for the exceptions feature
     * must complete without any exception.
     *
     * <p>The original code crashed because a single PDPageContentStream was
     * held open for the whole scenario while the stub continuation method
     * only clamped Y rather than opening a new page, causing PDFBox to write
     * content at negative Y coordinates when the CRLF stack trace was rendered.</p>
     */
    @Test
    public void splitModeExceptionFeatureDoesNotCrash() throws Exception {
        List<CucumberFeature> features = parse(EXCEPTION_FEATURE_JSON);
        assertEquals("Should parse 1 feature", 1, features.size());
        assertEquals("Should have 5 scenarios", 5,
                features.get(0).getActualScenarios().size());

        File pdf = tempPdf("split-exceptions");
        // Must not throw
        new FeaturePdfGenerator().generateFeaturePdf(features.get(0), pdf.getAbsolutePath());

        assertTrue("PDF must exist",      pdf.exists());
        assertTrue("PDF must be non-empty", pdf.length() > 0);
    }

    /**
     * The generated PDF must be a valid document with at least one page per scenario
     * (summary page + up to 5 detail pages = at least 2 pages total).
     */
    @Test
    public void splitModeExceptionFeaturePdfHasExpectedPages() throws Exception {
        List<CucumberFeature> features = parse(EXCEPTION_FEATURE_JSON);
        File pdf = tempPdf("split-exceptions-pages");
        new FeaturePdfGenerator().generateFeaturePdf(features.get(0), pdf.getAbsolutePath());

        try (PDDocument doc = PDDocument.load(pdf)) {
            // 1 feature summary page + 5 scenario pages (each is at least 1 page)
            assertTrue("At least 6 pages (1 summary + 5 scenarios)",
                    doc.getNumberOfPages() >= 6);
        }
    }

    /**
     * The failed scenario names and partial error text must be readable in the PDF.
     */
    @Test
    public void splitModeExceptionFeaturePdfContainsErrorContent() throws Exception {
        List<CucumberFeature> features = parse(EXCEPTION_FEATURE_JSON);
        File pdf = tempPdf("split-exceptions-content");
        new FeaturePdfGenerator().generateFeaturePdf(features.get(0), pdf.getAbsolutePath());

        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("Feature name in PDF",      text.contains("Exception"));
            assertTrue("Passing scenario in PDF",  text.contains("No Exception"));
            // Error content — first line of the stack trace
            assertTrue("AssertionError in PDF",    text.contains("AssertionError"));
        }
    }

    /**
     * The long stack trace (40 frames) must overflow onto a new page without
     * crashing.  The resulting PDF should have more than 1 page for the feature.
     */
    @Test
    public void splitModeLongStackTraceOverflowsToNewPage() throws Exception {
        List<CucumberFeature> features = parse(buildLongStackTraceJson());
        assertEquals(1, features.size());

        File pdf = tempPdf("split-long-stack");
        new FeaturePdfGenerator().generateFeaturePdf(features.get(0), pdf.getAbsolutePath());

        assertTrue("PDF must be non-empty", pdf.length() > 0);
        try (PDDocument doc = PDDocument.load(pdf)) {
            // 1 summary page + 1 scenario page that overflows into at least 1 continuation
            assertTrue("At least 3 pages (summary + scenario + overflow)",
                    doc.getNumberOfPages() >= 3);
        }
    }

    // -----------------------------------------------------------------------
    // Bug 2 tests — step/hook screenshots not rendered
    // -----------------------------------------------------------------------

    /**
     * Bug 2 regression: a split PDF for a feature with step-level embeddings
     * must be larger than a PDF for a feature without embeddings.
     *
     * <p>The original code never called {@code step.getEmbeddings()} in
     * split mode, so all screenshots were silently dropped.</p>
     */
    @Test
    public void splitModeStepEmbeddingMakesPdfLarger() throws Exception {
        // Parse both features
        List<CucumberFeature> withImages    = parse(STEP_SCREENSHOT_JSON);
        List<CucumberFeature> withoutImages = parse(buildNoImageJson());

        File pdfWith    = tempPdf("split-with-images");
        File pdfWithout = tempPdf("split-without-images");

        new FeaturePdfGenerator().generateFeaturePdf(withImages.get(0),    pdfWith.getAbsolutePath());
        new FeaturePdfGenerator().generateFeaturePdf(withoutImages.get(0), pdfWithout.getAbsolutePath());

        assertTrue("PDF with images must be non-empty",    pdfWith.length()    > 0);
        assertTrue("PDF without images must be non-empty", pdfWithout.length() > 0);
        assertTrue("PDF with images must be larger than PDF without images",
                pdfWith.length() > pdfWithout.length());
    }

    /**
     * Two step-level screenshots must result in a PDF with at least 2 pages
     * (summary + detail page; the detail page itself may be 1 page since
     * the 1×1 images are tiny, but generation must not throw).
     */
    @Test
    public void splitModeStepEmbeddingGeneratesWithoutException() throws Exception {
        List<CucumberFeature> features = parse(STEP_SCREENSHOT_JSON);
        assertEquals(1, features.size());

        // Verify parser found 2 embeddings on the step
        List<String> shots = features.get(0).getActualScenarios().get(0)
                .getAllScreenshots();
        assertEquals("Parser must find 2 embeddings on the step", 2, shots.size());

        File pdf = tempPdf("split-step-shots");
        new FeaturePdfGenerator().generateFeaturePdf(features.get(0), pdf.getAbsolutePath());

        assertTrue("PDF must exist",       pdf.exists());
        assertTrue("PDF must be non-empty", pdf.length() > 0);
        try (PDDocument doc = PDDocument.load(pdf)) {
            assertTrue("At least 2 pages", doc.getNumberOfPages() >= 2);
        }
    }

    /**
     * After-hook embeddings must also be rendered.
     */
    @Test
    public void splitModeAfterHookEmbeddingRendered() throws Exception {
        List<CucumberFeature> features = parse(AFTER_HOOK_SCREENSHOT_JSON);
        assertEquals(1, features.size());

        List<String> shots = features.get(0).getActualScenarios().get(0).getAllScreenshots();
        assertEquals("Parser must find 1 after-hook embedding", 1, shots.size());

        File pdf = tempPdf("split-after-hook-shot");
        new FeaturePdfGenerator().generateFeaturePdf(features.get(0), pdf.getAbsolutePath());

        assertTrue("PDF must be non-empty", pdf.length() > 0);
    }

    // -----------------------------------------------------------------------
    // getAllScreenshots() fix — before-hook embeddings now included
    // -----------------------------------------------------------------------

    /**
     * Before-hook embeddings must be returned by {@code getAllScreenshots()}.
     *
     * <p>Prior to the fix, only step + after-hook embeddings were collected.
     * A screenshot captured by a failing {@code @Before} hook was silently
     * dropped from both the split-mode renderer and the consolidated
     * Expanded section.</p>
     */
    @Test
    public void getAllScreenshotsIncludesBeforeHookEmbeddings() throws Exception {
        List<CucumberFeature> features = parse(BEFORE_HOOK_SCREENSHOT_JSON);
        assertEquals(1, features.size());

        CucumberScenario sc = features.get(0).getActualScenarios().get(0);
        assertFalse("Before hooks must be present", sc.getBeforeHooks().isEmpty());
        assertFalse("Before hook must have an embedding",
                sc.getBeforeHooks().get(0).getEmbeddings().isEmpty());

        List<String> shots = sc.getAllScreenshots();
        assertEquals("getAllScreenshots() must return the before-hook embedding", 1, shots.size());
    }

    /**
     * A split PDF for a feature whose before-hook has a screenshot must
     * generate without exception.
     */
    @Test
    public void splitModeBeforeHookEmbeddingRendered() throws Exception {
        List<CucumberFeature> features = parse(BEFORE_HOOK_SCREENSHOT_JSON);

        File pdf = tempPdf("split-before-hook-shot");
        new FeaturePdfGenerator().generateFeaturePdf(features.get(0), pdf.getAbsolutePath());

        assertTrue("PDF must be non-empty", pdf.length() > 0);
    }

    // -----------------------------------------------------------------------
    // Split mode — general correctness
    // -----------------------------------------------------------------------

    /**
     * Filename generation for a feature with a qTest tag.
     */
    @Test
    public void generateFilenameWithQtestTag() {
        List<CucumberFeature> features;
        try {
            features = parse("[{\"name\":\"My Feature\","
                    + "\"keyword\":\"Feature\","
                    + "\"uri\":\"f.feature\","
                    + "\"tags\":[{\"name\":\"@QTEST_TC_9999\"}],"
                    + "\"elements\":[]}]");
        } catch (Exception e) {
            fail("Parse threw: " + e.getMessage());
            return;
        }
        String filename = FeaturePdfGenerator.generateFilename(features.get(0), "9999");
        assertEquals("my-feature@QTEST_TC_9999.pdf", filename);
    }

    /**
     * Filename generation for a feature without a qTest tag.
     */
    @Test
    public void generateFilenameWithoutQtestTag() {
        List<CucumberFeature> features;
        try {
            features = parse("[{\"name\":\"No Tag Feature\","
                    + "\"keyword\":\"Feature\","
                    + "\"uri\":\"f.feature\","
                    + "\"tags\":[],"
                    + "\"elements\":[]}]");
        } catch (Exception e) {
            fail("Parse threw: " + e.getMessage());
            return;
        }
        String filename = FeaturePdfGenerator.generateFilename(features.get(0), null);
        assertEquals("no-tag-feature.pdf", filename);
    }

    /**
     * A feature with all passing scenarios generates a valid PDF.
     */
    @Test
    public void splitModeAllPassingFeatureGeneratesValidPdf() throws Exception {
        String json = "[{\"name\":\"All Passing\","
                + "\"keyword\":\"Feature\","
                + "\"uri\":\"passing.feature\","
                + "\"tags\":[],"
                + "\"elements\":["
                + "  {\"name\":\"Scenario A\",\"type\":\"scenario\",\"keyword\":\"Scenario\","
                + "   \"steps\":[{\"keyword\":\"Given \",\"name\":\"something is true\","
                + "     \"result\":{\"status\":\"passed\",\"duration\":100000000}}]},"
                + "  {\"name\":\"Scenario B\",\"type\":\"scenario\",\"keyword\":\"Scenario\","
                + "   \"steps\":[{\"keyword\":\"Then \",\"name\":\"something else is true\","
                + "     \"result\":{\"status\":\"passed\",\"duration\":50000000}}]}"
                + "]}]";

        List<CucumberFeature> features = parse(json);
        File pdf = tempPdf("split-all-passing");
        new FeaturePdfGenerator().generateFeaturePdf(features.get(0), pdf.getAbsolutePath());

        assertTrue("PDF must be non-empty", pdf.length() > 0);
        try (PDDocument doc = PDDocument.load(pdf)) {
            // 1 summary + 2 scenario pages
            assertEquals("3 pages", 3, doc.getNumberOfPages());
            String text = new PDFTextStripper().getText(doc);
            assertTrue("Feature name", text.contains("All Passing"));
            assertTrue("Scenario A",   text.contains("Scenario A"));
            assertTrue("Scenario B",   text.contains("Scenario B"));
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** A plain single-step feature with no embeddings — baseline for size comparison. */
    private static String buildNoImageJson() {
        return "[{\"name\":\"No Image Feature\","
                + "\"keyword\":\"Feature\","
                + "\"uri\":\"noimage.feature\","
                + "\"elements\":["
                + "  {\"name\":\"Plain scenario\",\"type\":\"scenario\",\"keyword\":\"Scenario\","
                + "   \"steps\":[{\"keyword\":\"Given \",\"name\":\"I do something\","
                + "     \"result\":{\"status\":\"passed\",\"duration\":500000000}}]}"
                + "]}]";
    }

    private static List<CucumberFeature> parse(String json) throws Exception {
        File f = Files.createTempFile("split-it-test", ".json").toFile();
        try (FileWriter w = new FileWriter(f)) { w.write(json); }
        return new CucumberJsonParser(false).parseJsonFile(f.getAbsolutePath());
    }

    private static File tempPdf(String prefix) throws Exception {
        return Files.createTempFile(prefix, ".pdf").toFile();
    }
}
