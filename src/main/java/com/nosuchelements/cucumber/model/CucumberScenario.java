package com.nosuchelements.cucumber.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a single Cucumber scenario (type="scenario") parsed from JSON.
 *
 * Stores the scenario's steps, before/after hooks, and optional background
 * steps that were paired with this scenario during parsing.
 */
public class CucumberScenario {

    private String name   = "";
    private String type   = "scenario";
    private List<String>        tags            = new ArrayList<>();
    private List<CucumberStep>  steps           = new ArrayList<>();
    private List<CucumberStep>  beforeHooks     = new ArrayList<>();
    private List<CucumberStep>  afterHooks      = new ArrayList<>();
    private List<CucumberStep>  backgroundSteps = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Computed status
    // -----------------------------------------------------------------------

    /**
     * Returns "FAILED", "SKIPPED", or "PASSED".
     * Before-hook failures are also counted as FAILED.
     */
    public String getStatus() {
        // Before-hook failure → whole scenario failed
        for (CucumberStep h : beforeHooks) {
            String s = h.getStatus();
            if ("failed".equalsIgnoreCase(s)) return "FAILED";
        }
        // Step statuses
        boolean anyFailed  = steps.stream().anyMatch(s -> "failed".equalsIgnoreCase(s.getStatus()));
        if (anyFailed) return "FAILED";
        boolean anySkipped = steps.stream().anyMatch(s ->
                "skipped".equalsIgnoreCase(s.getStatus())
                        || "undefined".equalsIgnoreCase(s.getStatus())
                        || "pending".equalsIgnoreCase(s.getStatus()));
        if (anySkipped) return "SKIPPED";
        return "PASSED";
    }

    // -----------------------------------------------------------------------
    // Step counts
    // -----------------------------------------------------------------------

    public int getTotalSteps()   { return steps.size(); }

    public int getPassedSteps()  {
        return (int) steps.stream()
                .filter(s -> "passed".equalsIgnoreCase(s.getStatus())).count();
    }

    public int getFailedSteps()  {
        return (int) steps.stream()
                .filter(s -> "failed".equalsIgnoreCase(s.getStatus())).count();
    }

    public int getSkippedSteps() {
        return (int) steps.stream()
                .filter(s -> !"passed".equalsIgnoreCase(s.getStatus())
                        && !"failed".equalsIgnoreCase(s.getStatus())).count();
    }

    // -----------------------------------------------------------------------
    // Duration
    // -----------------------------------------------------------------------

    /** Total scenario duration in milliseconds. */
    public long getDurationMillis() {
        return steps.stream().mapToLong(CucumberStep::getDurationMillis).sum();
    }

    /** Formatted duration string. */
    public String formatDuration() {
        long ms = getDurationMillis();
        if (ms < 1000) return ms + "ms";
        if (ms < 60_000) return String.format("%.1fs", ms / 1000.0);
        long m = ms / 60_000;
        long s = (ms % 60_000) / 1000;
        return m + "m " + s + "s";
    }

    // -----------------------------------------------------------------------
    // Screenshots
    // -----------------------------------------------------------------------

    /**
     * Returns all base64 screenshot strings from step embeddings and
     * hook embeddings in source order: before-hooks, steps, after-hooks.
     * PNG and JPEG mime types are included.
     */
    public List<String> getAllScreenshots() {
        List<String> shots = new ArrayList<>();
        for (CucumberStep hook : beforeHooks) {
            shots.addAll(hook.getEmbeddings());
        }
        for (CucumberStep step : steps) {
            shots.addAll(step.getEmbeddings());
        }
        for (CucumberStep hook : afterHooks) {
            shots.addAll(hook.getEmbeddings());
        }
        return shots;
    }

    // -----------------------------------------------------------------------
    // Background
    // -----------------------------------------------------------------------

    public boolean hasBackground() {
        return backgroundSteps != null && !backgroundSteps.isEmpty();
    }

    public List<CucumberStep> getBackgroundSteps() {
        return backgroundSteps != null ? backgroundSteps : new ArrayList<>();
    }

    public void setBackgroundSteps(List<CucumberStep> steps) {
        this.backgroundSteps = steps != null ? steps : new ArrayList<>();
    }

    // -----------------------------------------------------------------------
    // Getters / Setters
    // -----------------------------------------------------------------------

    public String getName()                      { return name; }
    public void   setName(String v)              { this.name = v != null ? v : ""; }

    public String getType()                      { return type; }
    public void   setType(String v)              { this.type = v; }

    public List<String> getTags()                { return tags; }
    public void   setTags(List<String> v)        { this.tags = v != null ? v : new ArrayList<>(); }

    public List<CucumberStep> getSteps()         { return steps; }
    public void   setSteps(List<CucumberStep> v) { this.steps = v != null ? v : new ArrayList<>(); }

    public List<CucumberStep> getBeforeHooks()   { return beforeHooks; }
    public void setBeforeHooks(List<CucumberStep> v) {
        this.beforeHooks = v != null ? v : new ArrayList<>();
    }

    public List<CucumberStep> getAfterHooks()    { return afterHooks; }
    public void setAfterHooks(List<CucumberStep> v) {
        this.afterHooks = v != null ? v : new ArrayList<>();
    }
}
