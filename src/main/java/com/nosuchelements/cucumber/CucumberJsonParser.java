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
 * <p>Handles the full Cucumber JSON format including:
 * <ul>
 *   <li>Feature-level and scenario-level tags</li>
 *   <li>Background elements (paired to following scenarios)</li>
 *   <li>Before/after hooks with embeddings</li>
 *   <li>Step embeddings (screenshots as base64 PNG/JPEG)</li>
 *   <li>DataTable rows</li>
 *   <li>DocString blocks</li>
 *   <li>Output lines</li>
 * </ul>
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

        // Result
        if (obj.has("result")) {
            JsonObject result = obj.getAsJsonObject("result");
            step.setStatus(str(result, "status"));
            if (result.has("duration")) {
                step.setDurationNanos(result.get("duration").getAsLong());
            }
            if (result.has("error_message")) {
                step.setErrorMessage(str(result, "error_message"));
            }
            // Embeddings on result (after-hook style)
            step.getEmbeddings().addAll(parseEmbeddings(result));
        }

        // Embeddings on the step directly (step-level screenshots)
        step.getEmbeddings().addAll(parseEmbeddings(obj));
        // Location 3: Cucumber 7 @AfterStep — your hook pattern writes here
        if (obj.has("after")) {
           for (JsonElement afterEl : obj.getAsJsonArray("after")) {
               JsonObject afterObj = afterEl.getAsJsonObject();
               step.getEmbeddings().addAll(parseEmbeddings(afterObj));
            if (afterObj.has("result")) {
               step.getEmbeddings().addAll(parseEmbeddings(afterObj.getAsJsonObject("result")));
                }
              }
            }
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

        // DocString
        if (obj.has("doc_string")) {
            JsonObject ds = obj.getAsJsonObject("doc_string");
            CucumberDocString docString = new CucumberDocString();
            docString.setContent(str(ds, "content"));
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

    private List<String> parseEmbeddings(JsonObject obj) {
        List<String> result = new ArrayList<>();
        if (!obj.has("embeddings")) return result;
        for (JsonElement el : obj.getAsJsonArray("embeddings")) {
            JsonObject emb = el.getAsJsonObject();
            String mime = str(emb, "mime_type");
            if (mime.startsWith("image/") && emb.has("data")) {
                result.add(emb.get("data").getAsString());
            }
        }
        return result;
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
