package com.nosuchelements.consolidated;

import com.nosuchelements.cucumber.model.CucumberFeature;
import com.nosuchelements.cucumber.model.CucumberScenario;

import java.util.List;

/**
 * Aggregated statistics computed from all parsed features.
 * Immutable after construction.
 */
public class ReportStats {

    public final int totalFeatures;
    public final int passedFeatures;
    public final int failedFeatures;
    public final int skippedFeatures;

    public final int totalScenarios;
    public final int passedScenarios;
    public final int failedScenarios;
    public final int skippedScenarios;

    public final int totalSteps;
    public final int passedSteps;
    public final int failedSteps;
    public final int skippedSteps;

    public final long totalDurationMs;

    private ReportStats(int totalFeatures, int passedFeatures, int failedFeatures,
                        int skippedFeatures, int totalScenarios, int passedScenarios,
                        int failedScenarios, int skippedScenarios, int totalSteps,
                        int passedSteps, int failedSteps, int skippedSteps,
                        long totalDurationMs) {
        this.totalFeatures    = totalFeatures;
        this.passedFeatures   = passedFeatures;
        this.failedFeatures   = failedFeatures;
        this.skippedFeatures  = skippedFeatures;
        this.totalScenarios   = totalScenarios;
        this.passedScenarios  = passedScenarios;
        this.failedScenarios  = failedScenarios;
        this.skippedScenarios = skippedScenarios;
        this.totalSteps       = totalSteps;
        this.passedSteps      = passedSteps;
        this.failedSteps      = failedSteps;
        this.skippedSteps     = skippedSteps;
        this.totalDurationMs  = totalDurationMs;
    }

    /**
     * Compute aggregate statistics from a list of parsed features.
     */
    public static ReportStats compute(List<CucumberFeature> features) {
        int tf = 0, pf = 0, ff = 0, skf = 0;
        int ts = 0, ps = 0, fs = 0, sks = 0;
        int tstep = 0, pstep = 0, fstep = 0, skstep = 0;
        long dur = 0L;

        for (CucumberFeature feature : features) {
            tf++;
            String fStatus = feature.getOverallStatus();
            if      ("FAILED".equals(fStatus))  ff++;
            else if ("SKIPPED".equals(fStatus)) skf++;
            else                                pf++;

            ts    += feature.getTotalScenarios();
            ps    += feature.getPassedScenarios();
            fs    += feature.getFailedScenarios();
            sks   += feature.getSkippedScenarios();

            tstep  += feature.getTotalSteps();
            pstep  += feature.getPassedSteps();
            fstep  += feature.getFailedSteps();
            skstep += feature.getSkippedSteps();

            for (CucumberScenario sc : feature.getActualScenarios()) {
                dur += sc.getDurationMillis();
            }
        }

        return new ReportStats(tf, pf, ff, skf, ts, ps, fs, sks,
                tstep, pstep, fstep, skstep, dur);
    }

    /** Overall run status. */
    public String getOverallStatus() {
        if (failedSteps  > 0) return "FAILED";
        if (skippedSteps > 0) return "SKIPPED";
        return "PASSED";
    }

    /** Format total duration compactly. */
    public String formatDuration() {
        if (totalDurationMs < 1000) return totalDurationMs + "ms";
        if (totalDurationMs < 60_000) return String.format("%.1fs", totalDurationMs / 1000.0);
        long m = totalDurationMs / 60_000;
        long s = (totalDurationMs % 60_000) / 1000;
        return m + "m " + s + "s";
    }

    /** Pass rate 0-100 for display. */
    public int passRatePercent(int passed, int total) {
        if (total <= 0) return 0;
        return (int) Math.round(100.0 * passed / total);
    }
}
