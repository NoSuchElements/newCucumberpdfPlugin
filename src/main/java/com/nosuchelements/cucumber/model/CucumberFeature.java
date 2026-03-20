package com.nosuchelements.cucumber.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Cucumber feature parsed from JSON.
 *
 * Aggregates scenario-level statistics for use in Dashboard / Features sections.
 */
public class CucumberFeature {

    private String name    = "";
    private String uri     = "";
    private String keyword = "Feature";

    private List<String>          tags      = new ArrayList<>();
    private List<CucumberScenario> scenarios = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Scenario access
    // -----------------------------------------------------------------------

    /**
     * Returns only actual scenarios (type="scenario"), excluding background elements.
     */
    public List<CucumberScenario> getActualScenarios() {
        List<CucumberScenario> result = new ArrayList<>();
        for (CucumberScenario sc : scenarios) {
            if (!"background".equalsIgnoreCase(sc.getType())) {
                result.add(sc);
            }
        }
        return result;
    }

    /** Returns all scenario elements including background. */
    public List<CucumberScenario> getScenarios() { return scenarios; }

    // -----------------------------------------------------------------------
    // Aggregate status
    // -----------------------------------------------------------------------

    public String getOverallStatus() {
        for (CucumberScenario sc : getActualScenarios()) {
            if ("FAILED".equals(sc.getStatus())) return "FAILED";
        }
        for (CucumberScenario sc : getActualScenarios()) {
            if ("SKIPPED".equals(sc.getStatus())) return "SKIPPED";
        }
        return "PASSED";
    }

    // -----------------------------------------------------------------------
    // Aggregate counts
    // -----------------------------------------------------------------------

    public int getTotalScenarios() {
        return (int) getActualScenarios().size();
    }

    public int getPassedScenarios() {
        return (int) getActualScenarios().stream()
                .filter(sc -> "PASSED".equals(sc.getStatus())).count();
    }

    public int getFailedScenarios() {
        return (int) getActualScenarios().stream()
                .filter(sc -> "FAILED".equals(sc.getStatus())).count();
    }

    public int getSkippedScenarios() {
        return (int) getActualScenarios().stream()
                .filter(sc -> "SKIPPED".equals(sc.getStatus())).count();
    }

    public int getTotalSteps() {
        return getActualScenarios().stream().mapToInt(CucumberScenario::getTotalSteps).sum();
    }

    public int getPassedSteps() {
        return getActualScenarios().stream().mapToInt(CucumberScenario::getPassedSteps).sum();
    }

    public int getFailedSteps() {
        return getActualScenarios().stream().mapToInt(CucumberScenario::getFailedSteps).sum();
    }

    public int getSkippedSteps() {
        return getActualScenarios().stream().mapToInt(CucumberScenario::getSkippedSteps).sum();
    }

    // -----------------------------------------------------------------------
    // Tag extraction for Case ID
    // -----------------------------------------------------------------------

    /**
     * Searches through feature-level tags for one matching the given prefix.
     * Returns the matching tag (with @ symbol), or "UNKNOWN" if not found.
     * Used to populate the "Case ID" column in Dashboard and Features sections.
     *
     * @param prefix the tag prefix to search for (e.g., "QTEST_TC_", "@QTEST_TC_")
     * @return the matching tag name (e.g., "@QTEST_TC_1001") or "UNKNOWN"
     */
    public String extractQtestTag(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "UNKNOWN";
        }

        // Normalize prefix: remove leading @ if present, ensure uppercase
        String searchPrefix = prefix.startsWith("@")
                ? prefix.substring(1).toUpperCase()
                : prefix.toUpperCase();

        // Search through feature tags
        for (String tag : tags) {
            if (tag == null) continue;
            // Normalize tag: remove leading @, ensure uppercase
            String normalizedTag = tag.startsWith("@")
                    ? tag.substring(1).toUpperCase()
                    : tag.toUpperCase();
            // Check if tag starts with prefix
            if (normalizedTag.startsWith(searchPrefix)) {
                // Return original tag with @ symbol for display
                return tag.startsWith("@") ? tag : "@" + tag;
            }
        }

        return "UNKNOWN";
    }

    // -----------------------------------------------------------------------
    // Getters / Setters
    // -----------------------------------------------------------------------

    public String getName()                       { return name; }
    public void   setName(String v)               { this.name = v != null ? v : ""; }

    public String getUri()                        { return uri; }
    public void   setUri(String v)                { this.uri = v; }

    public String getKeyword()                    { return keyword; }
    public void   setKeyword(String v)            { this.keyword = v; }

    public List<String> getTags()                 { return tags; }
    public void   setTags(List<String> v)         { this.tags = v != null ? v : new ArrayList<>(); }

    public void   setScenarios(List<CucumberScenario> v) {
        this.scenarios = v != null ? v : new ArrayList<>();
    }
}
