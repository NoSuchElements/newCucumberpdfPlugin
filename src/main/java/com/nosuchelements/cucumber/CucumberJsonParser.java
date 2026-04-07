package com.nosuchelements.cucumber;

import com.google.gson.*;
import com.nosuchelements.cucumber.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Parses Cucumber JSON report files into the internal model.
 *
 * <p>Handles the full Cucumber JSON format including:</p>
 * <ul>
 *   <li>Feature-level and scenario-level tags</li>
 *   <li>Background elements (paired to following scenarios)</li>
 *   <li>Before/after hooks with embeddings</li>
 *   <li>Step embeddings (screenshots as base64 PNG/JPEG)</li>
 *   <li>DataTable rows</li>
 *   <li>DocString blocks</li>
 *   <li>Output lines</li>
 * </ul>
 *
 * <h2>Embedding Compatibility Matrix</h2>
 * <p>All Cucumber JVM versions (4.x–7.x) are fully supported:</p>
 * <pre>
 * JSON location                        Cucumber ver  API                      Field name
 * step["embeddings"]                   4.x           scenario.embed() in step mime_type
 * step["result"]["embeddings"]         4/5           result-level             mime_type
 * step["result"]["embeddings"]         5/6           result-level             mimetype (no _)
 * step["after"][n]["embeddings"]       7.x           @AfterStep               mime_type + optional name
 * scenario["after"][n]["embeddings"]   4/7           @After                   mime_type
 * </pre>
 * <p>The helper {@link #getMimeType(JsonObject)} resolves the field regardless of
 * underscore variant, so no caller needs to know which version produced the JSON.</p>
 */
public class CucumberJsonParser {

    private static final Logger log = LoggerFactory.getLogger(CucumberJsonParser.class);

    private final boolean verbose;
    private final String  tagPrefix;

    /** Minimal constructor used by tests (verbose=false, tagPrefix=default). */
    public CucumberJsonParser(boolean verbose) {
        this(verbose, "QTEST_TC_");
    }

    public CucumberJsonParser(boolean verbose, String tagPrefix) {
        this.verbose   = verbose;
        this.tagPrefix = tagPrefix != null ? tagPrefix : "QTEST_TC_";
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Parse a single Cucumber JSON file.
     *
     * @param filePath absolute path to the JSON file
     * @return list of parsed features (never null, may be empty)
     */
    public List<CucumberFeature> parseJsonFile(String filePath) throws IOException {
        String json = new String(Files.readAllBytes(Paths.get(filePath)));
        return parseJson(json);
    }

    /**
     * Extract the qTest case ID tag from a feature, using the configured prefix.
     * Returns null if no matching tag is found.
     */
    public String extractQtestCaseId(CucumberFeature feature) {
        for (String tag : feature.getTags()) {
            String clean = tag.startsWith("@") ? tag.substring(1) : tag;
            if (clean.startsWith(tagPrefix)) {
                return clean.substring(tagPrefix.length());
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Parsing
    // -----------------------------------------------------------------------

    private List<CucumberFeature> parseJson(String json) {
        List<CucumberFeature> features = new ArrayList<>();
        try {
            JsonArray arr = JsonParser.parseString(json.trim()).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                CucumberFeature feature = parseFeature(obj);
                features.add(feature);
            }
        } catch (Exception e) {
            log.error("Failed to parse Cucumber JSON: {}", e.getMessage(), e);
        }
        if (verbose) {
            log.info("Parsed {} features", features.size());
        }
        return features;
    }

    private CucumberFeature parseFeature(JsonObject obj) {
        CucumberFeature feature = new CucumberFeature();
        feature.setName(str(obj, "name"));
        feature.setUri(str(obj, "uri"));
        feature.setKeyword(str(obj, "keyword"));
        feature.setTags(parseTags(obj));

        List<CucumberScenario> scenarios = new ArrayList<>();
        CucumberScenario lastBackground  = null;

        if (obj.has("elements")) {
            for (JsonElement el : obj.getAsJsonArray("elements")) {
                JsonObject sObj = el.getAsJsonObject();
                String type = str(sObj, "type");

                if ("background".equalsIgnoreCase(type)) {
                    // Parse background steps for pairing
                    lastBackground = new CucumberScenario();
                    lastBackground.setType("background");
                    lastBackground.setSteps(parseSteps(sObj, "steps"));

                } else {
                    CucumberScenario sc = parseScenario(sObj);
                    // Pair background steps
                    if (lastBackground != null) {
                        sc.setBackgroundSteps(lastBackground.getSteps());
                    }
                    scenarios.add(sc);
                }
            }
        }
        feature.setScenarios(scenarios);
        return feature;
    }

    private CucumberScenario parseScenario(JsonObject obj) {
        CucumberScenario sc = new CucumberScenario();
        sc.setName(str(obj, "name"));
        sc.setType(str(obj, "type"));
        sc.setTags(parseTags(obj));
        sc.setSteps(parseSteps(obj, "steps"));
        sc.setBeforeHooks(parseHooks(obj, "before"));
        sc.setAfterHooks(parseHooks(obj, "after"));
        return sc;
    }

    private List<CucumberStep> parseSteps(JsonObject obj, String key) {
        List<CucumberStep> steps = new ArrayList<>();
        if (!obj.has(key)) return steps;
        for (JsonElement el : obj.getAsJsonArray(key)) {
            steps.add(parseStep(el.getAsJsonObject(), false));
        }
        return steps;
    }

    private List<CucumberStep> parseHooks(JsonObject obj, String key) {
        List<CucumberStep> hooks = new ArrayList<>();
        if (!obj.has(key)) return hooks;
        for (JsonElement el : obj.getAsJsonArray(key)) {
            hooks.add(parseStep(el.getAsJsonObject(), true));
        }
        return hooks;
    }

    private CucumberStep parseStep(JsonObject obj, boolean isHook) {
        CucumberStep step = new CucumberStep();

        if (!isHook) {
            step.setKeyword(str(obj, "keyword"));
            step.setName(str(obj, "name"));
        }

        // Result block — carries status, duration, error, and result-level embeddings
        // (Cucumber 4/5 place embeddings here; Cucumber 5/6 use "mimetype" without underscore)
        if (obj.has("result")) {
            JsonObject result = obj.getAsJsonObject("result");
            step.setStatus(str(result, "status"));
            if (result.has("duration")) {
                step.setDurationNanos(result.get("duration").getAsLong());
            }
            if (result.has("error_message")) {
                step.setErrorMessage(str(result, "error_message"));
            }
            // Cucumber 4/5: embeddings nested inside result
            step.getEmbeddings().addAll(parseEmbeddings(result));
        }

        // Step-level embeddings (Cucumber 4.x scenario.embed() called inside a step body,
        // and Cucumber 7.x @AfterStep hook — both write here; field is "mime_type" or "mimetype")
        step.getEmbeddings().addAll(parseEmbeddings(obj));

        // DataTable rows
        if (obj.has("rows")) {
            List<CucumberTableRow> rows = new ArrayList<>();
            for (JsonElement rowEl : obj.getAsJsonArray("rows")) {
                JsonObject rowObj = rowEl.getAsJsonObject();
                List<String> cells = new ArrayList<>();
                if (rowObj.has("cells")) {
                    for (JsonElement c : rowObj.getAsJsonArray("cells")) {
                        cells.add(c.getAsString());
                    }
                }
                rows.add(new CucumberTableRow(cells));
            }
            step.setDataTableRows(rows);
        }

        // DocString — Cucumber 7+ uses "content", older versions used "value"
        if (obj.has("doc_string")) {
            JsonObject ds = obj.getAsJsonObject("doc_string");
            CucumberDocString docString = new CucumberDocString();
            // Prefer "content" (Cucumber 7+), fall back to "value" (Cucumber 4/5/6)
            if (ds.has("content")) {
                docString.setContent(str(ds, "content"));
            } else if (ds.has("value")) {
                docString.setContent(str(ds, "value"));
            }
            docString.setContentType(str(ds, "content_type"));
            step.setDocString(docString);
        }

        // Output lines
        if (obj.has("output")) {
            List<String> lines = new ArrayList<>();
            for (JsonElement line : obj.getAsJsonArray("output")) {
                lines.add(line.getAsString());
            }
            step.setOutputLines(lines);
        }

        return step;
    }

    /**
     * Extracts base64-encoded image data from an "embeddings" array on the given object.
     *
     * <p>Compatibility matrix handled here:</p>
     * <ul>
     *   <li>Cucumber 4.x / 5.x / 7.x @After: field is {@code "mime_type"} (with underscore)</li>
     *   <li>Cucumber 5.x / 6.x result-level: field is {@code "mimetype"} (no underscore)</li>
     *   <li>Cucumber 7.x @AfterStep: field is {@code "mime_type"}, optional {@code "name"} field present</li>
     * </ul>
     * <p>The {@link #getMimeType(JsonObject)} helper resolves both variants transparently.</p>
     *
     * @param obj a JSON object that may contain an "embeddings" array
     * @return list of base64 strings for all image/* embeddings found; empty if none
     */
    private List<String> parseEmbeddings(JsonObject obj) {
        List<String> result = new ArrayList<>();
        if (!obj.has("embeddings")) return result;
        for (JsonElement el : obj.getAsJsonArray("embeddings")) {
            if (!el.isJsonObject()) continue;
            JsonObject emb = el.getAsJsonObject();
            String mime = getMimeType(emb);
            if (mime.startsWith("image/") && emb.has("data")) {
                result.add(emb.get("data").getAsString());
            }
        }
        return result;
    }

    /**
     * Resolves the MIME type from an embedding object, handling all Cucumber versions:
     * <ul>
     *   <li>{@code "mime_type"} — Cucumber 4.x, 5.x, 7.x (@After and @AfterStep hooks)</li>
     *   <li>{@code "mimetype"}  — Cucumber 5.x/6.x result-level embeddings (no underscore)</li>
     * </ul>
     * Checks {@code "mime_type"} first (more common), then falls back to {@code "mimetype"}.
     *
     * @param emb the embedding JSON object
     * @return the resolved MIME type string, or empty string if neither field is present
     */
    private static String getMimeType(JsonObject emb) {
        if (emb.has("mime_type") && !emb.get("mime_type").isJsonNull()) {
            return emb.get("mime_type").getAsString();
        }
        if (emb.has("mimetype") && !emb.get("mimetype").isJsonNull()) {
            return emb.get("mimetype").getAsString();
        }
        return "";
    }

    private List<String> parseTags(JsonObject obj) {
        List<String> tags = new ArrayList<>();
        if (!obj.has("tags")) return tags;
        for (JsonElement el : obj.getAsJsonArray("tags")) {
            if (el.isJsonObject()) {
                String name = str(el.getAsJsonObject(), "name");
                if (!name.isEmpty()) tags.add(name);
            } else {
                tags.add(el.getAsString());
            }
        }
        return tags;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return "";
        JsonElement el = obj.get(key);
        return el.isJsonNull() ? "" : el.getAsString();
    }
}
