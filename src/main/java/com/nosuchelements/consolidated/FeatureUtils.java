package com.nosuchelements.consolidated;

import com.nosuchelements.cucumber.model.CucumberFeature;

/**
 * Shared utilities for feature-level operations used across multiple sections.
 *
 * <p>Eliminates the duplicated {@code extractCaseId} private methods that existed
 * in both {@code DashboardSection} and {@code FeaturesSection}.</p>
 *
 * <p>Also owns the canonical {@code normaliseUri} helper to strip platform-specific
 * {@code file:} prefixes from feature URIs (fixes F4 Windows path bug).</p>
 */
public final class FeatureUtils {

    /** Plugin version — single source of truth for all footer strings. */
    public static final String VERSION = "1.2.1";

    private FeatureUtils() {}

    // -------------------------------------------------------------------------
    // Case-ID extraction  (was duplicated in DashboardSection + FeaturesSection)
    // -------------------------------------------------------------------------

    /**
     * Extract a case ID from the feature's tags using the configured tag prefix.
     *
     * <p>Normalises both the prefix and each tag by stripping a leading {@code @}
     * and performing a case-insensitive prefix comparison, so all of the following
     * match when {@code tagPrefix = "QTEST_TC_"}:</p>
     * <ul>
     *   <li>{@code @QTEST_TC_1001}  → {@code TC_1001}</li>
     *   <li>{@code @qtest_tc_5050}  → {@code TC_5050}</li>
     *   <li>{@code qtest_tc_99}     → {@code TC_99}</li>
     * </ul>
     *
     * @param f         feature to inspect
     * @param tagPrefix configured prefix (may be {@code null} or blank)
     * @return the formatted case ID, or {@code "NA"} when no matching tag is found
     */
    public static String extractCaseId(CucumberFeature f, String tagPrefix) {
        if (tagPrefix == null || tagPrefix.isBlank()) return "NA";

        String prefix = tagPrefix.startsWith("@")
                ? tagPrefix.substring(1).toUpperCase()
                : tagPrefix.toUpperCase();

        for (String tag : f.getTags()) {
            if (tag == null) continue;
            String normalised = tag.startsWith("@")
                    ? tag.substring(1).toUpperCase()
                    : tag.toUpperCase();
            if (normalised.startsWith(prefix)) {
                return "TC_" + normalised.substring(prefix.length());
            }
        }
        return "NA";
    }

    // -------------------------------------------------------------------------
    // URI normalisation  (fixes F4 – Windows file: URI prefix)
    // -------------------------------------------------------------------------

    /**
     * Strip the {@code file:} scheme and any leading slashes from a feature URI
     * so that it displays as a clean file-system path.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>{@code file:///C:/Users/ci/features/login.feature}  → {@code C:/Users/ci/features/login.feature}</li>
     *   <li>{@code file:/home/ci/features/login.feature}        → {@code home/ci/features/login.feature}</li>
     *   <li>{@code features/login.feature}                      → {@code features/login.feature}</li>
     * </ul>
     *
     * @param uri raw URI string from the Cucumber JSON; may be {@code null}
     * @return normalised path string (never {@code null})
     */
    public static String normaliseUri(String uri) {
        if (uri == null) return "";
        // Strip "file:" scheme prefix, then any run of leading slashes
        String s = uri.replaceFirst("(?i)^file:", "");
        // Remove leading slashes — but only up to 3 (///C:/... → C:/...)
        s = s.replaceFirst("^/{1,3}", "");
        return s;
    }
}
