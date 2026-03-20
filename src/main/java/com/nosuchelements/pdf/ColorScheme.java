package com.nosuchelements.pdf;

import java.awt.Color;

/**
 * Central colour palette used across all PDF sections.
 *
 * <p>All constants are {@code static} and mutable via {@link #apply(ColorSchemeConfig)}
 * so that users can override colours from the Maven plugin configuration.</p>
 *
 * <p>Note: All status-dispatch methods use if/else chains for Java 11 compatibility
 * (switch expressions require Java 14+).</p>
 */
public class ColorScheme {

    // ── Core palette ─────────────────────────────────────────────────────────
    public static Color HEADER         = hex("1E3A5F");
    public static Color PAGE_BG        = hex("F5F7FA");
    public static Color CARD_BG        = hex("FFFFFF");
    public static Color ROW_ALT        = hex("EBF2FA");

    // ── Status colours ───────────────────────────────────────────────────────
    public static Color PASSED         = hex("388E3C");
    public static Color PASSED_BG      = hex("E8F5E9");
    public static Color PASSED_TEXT    = hex("1B5E20");

    public static Color FAILED         = hex("C62828");
    public static Color FAILED_BG      = hex("FFEBEE");
    public static Color FAILED_TEXT    = hex("B71C1C");

    public static Color SKIPPED        = hex("E65100");
    public static Color SKIPPED_TEXT   = hex("BF360C");

    public static Color PENDING        = hex("F57C00");

    // ── Accent ───────────────────────────────────────────────────────────────
    public static Color ACCENT         = hex("2E75B6");

    // ── Text ─────────────────────────────────────────────────────────────────
    public static Color TEXT_WHITE     = hex("FFFFFF");
    public static Color TEXT_PRIMARY   = hex("1A1A1A");
    public static Color TEXT_SECONDARY = hex("333333");
    public static Color TEXT_MUTED     = hex("5A5A5A");
    public static Color TEXT_HINT      = hex("9E9E9E");

    // ── Borders ──────────────────────────────────────────────────────────────
    public static Color BORDER         = hex("B0BEC5");
    public static Color BORDER_SUBTLE  = hex("ECEFF1");

    // ── Derived ──────────────────────────────────────────────────────────────

    /** Background tint matching the scenario status. */
    public static Color bgForStatus(String status) {
        if (status == null) return CARD_BG;
        String s = status.toUpperCase();
        if ("FAILED".equals(s))  return FAILED_BG;
        if ("SKIPPED".equals(s)) return hex("FFF3E0");
        return PASSED_BG;
    }

    /** Foreground colour matching the scenario status. */
    public static Color forStatus(String status) {
        if (status == null) return TEXT_MUTED;
        String s = status.toUpperCase();
        if ("FAILED".equals(s))                            return FAILED;
        if ("SKIPPED".equals(s))                           return SKIPPED;
        if ("PENDING".equals(s) || "UNDEFINED".equals(s)) return PENDING;
        return PASSED;
    }

    /** Text colour for status labels. */
    public static Color textForStatus(String status) {
        if (status == null) return TEXT_MUTED;
        String s = status.toUpperCase();
        if ("FAILED".equals(s))  return FAILED_TEXT;
        if ("SKIPPED".equals(s)) return SKIPPED_TEXT;
        return PASSED_TEXT;
    }

    // ── Override from plugin configuration ───────────────────────────────────

    /**
     * Apply optional colour overrides from the Maven plugin {@code <colors>} block.
     * Only non-null fields in {@code config} replace the defaults.
     *
     * @param config may be null (no-op)
     */
    public static void apply(ColorSchemeConfig config) {
        if (config == null) return;
        if (config.getHeader()  != null) HEADER  = parseColor(config.getHeader());
        if (config.getPassed()  != null) PASSED  = parseColor(config.getPassed());
        if (config.getFailed()  != null) FAILED  = parseColor(config.getFailed());
        if (config.getSkipped() != null) SKIPPED = parseColor(config.getSkipped());
        if (config.getAccent()  != null) ACCENT  = parseColor(config.getAccent());
        if (config.getRowAlt()  != null) ROW_ALT = parseColor(config.getRowAlt());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Color hex(String hex) {
        long v = Long.parseLong(hex.replace("#", ""), 16);
        return new Color((int)((v >> 16) & 0xFF), (int)((v >> 8) & 0xFF), (int)(v & 0xFF));
    }

    private static Color parseColor(String s) {
        if (s == null || s.trim().isEmpty()) return CARD_BG;
        try { return hex(s.trim()); } catch (Exception e) { return CARD_BG; }
    }

    private ColorScheme() {}
}
