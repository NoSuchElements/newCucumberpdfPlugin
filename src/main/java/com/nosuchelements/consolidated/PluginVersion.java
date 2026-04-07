package com.nosuchelements.consolidated;

/**
 * Single source of truth for the plugin version string.
 *
 * <p>All footer labels, metadata fields, and log messages should reference
 * {@link #FULL} rather than embedding a string literal.</p>
 */
public final class PluginVersion {

    /** Full product label used in PDF footers and document metadata. */
    public static final String FULL = "Cucumber PDF Reporter v1.4.1";

    /** Bare version number (no product name). */
    public static final String NUMBER = "1.4.1";

    private PluginVersion() {}
}
