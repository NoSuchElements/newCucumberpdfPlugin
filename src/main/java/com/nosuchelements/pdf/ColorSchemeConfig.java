package com.nosuchelements.pdf;

/**
 * POJO that maps to the optional {@code <colors>} block in the Maven plugin
 * configuration. Each field corresponds to a colour that can be overridden.
 *
 * Example pom.xml usage:
 * <pre>{@code
 * <colors>
 *   <header>#0D2340</header>
 *   <passed>#2E7D32</passed>
 *   <failed>#B71C1C</failed>
 * </colors>
 * }</pre>
 */
public class ColorSchemeConfig {

    private String header;
    private String passed;
    private String failed;
    private String skipped;
    private String accent;
    private String rowAlt;

    public String getHeader()  { return header; }
    public void   setHeader(String v)  { this.header  = v; }

    public String getPassed()  { return passed; }
    public void   setPassed(String v)  { this.passed  = v; }

    public String getFailed()  { return failed; }
    public void   setFailed(String v)  { this.failed  = v; }

    public String getSkipped() { return skipped; }
    public void   setSkipped(String v) { this.skipped = v; }

    public String getAccent()  { return accent; }
    public void   setAccent(String v)  { this.accent  = v; }

    public String getRowAlt()  { return rowAlt; }
    public void   setRowAlt(String v)  { this.rowAlt  = v; }
}
