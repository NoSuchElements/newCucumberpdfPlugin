package com.nosuchelements.cucumber.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optional environment / build metadata attached to a report.
 *
 * <p>Populated from the Maven plugin configuration block {@code <metadata>}.
 * If not configured, no metadata block appears in the report.</p>
 *
 * <p>Typical use cases:</p>
 * <ul>
 *   <li>CI build info: job name, build number, branch, commit SHA</li>
 *   <li>Test environment: browser, OS, base URL, grid endpoint</li>
 *   <li>Application info: version under test, deployment tag</li>
 * </ul>
 *
 * <h3>Example Maven configuration</h3>
 * <pre>{@code
 * <metadata>
 *   <environment>QA</environment>
 *   <branch>${env.GIT_BRANCH}</branch>
 *   <build>${env.BUILD_NUMBER}</build>
 *   <appVersion>2.14.0-rc3</appVersion>
 * </metadata>
 * }</pre>
 */
public class ReportMetadata {

    /** Key insertion order is preserved for display. */
    private final Map<String, String> entries = new LinkedHashMap<>();

    public void put(String key, String value) {
        if (key != null && !key.isBlank() && value != null) {
            entries.put(key.trim(), value.trim());
        }
    }

    public Map<String, String> getEntries() { return entries; }

    public boolean isEmpty() { return entries.isEmpty(); }

    // -----------------------------------------------------------------------
    // Convenience setters (bound by Maven @Parameter on the inner POJO)
    // -----------------------------------------------------------------------

    private String environment;
    private String branch;
    private String build;
    private String appVersion;
    private String browser;
    private String os;
    private String baseUrl;

    public void setEnvironment(String v) { environment = v; put("Environment", v); }
    public void setBranch(String v)      { branch = v;      put("Branch",      v); }
    public void setBuild(String v)       { build = v;       put("Build",       v); }
    public void setAppVersion(String v)  { appVersion = v;  put("App Version", v); }
    public void setBrowser(String v)     { browser = v;     put("Browser",     v); }
    public void setOs(String v)          { os = v;          put("OS",          v); }
    public void setBaseUrl(String v)     { baseUrl = v;     put("Base URL",    v); }

    public String getEnvironment() { return environment; }
    public String getBranch()      { return branch; }
    public String getBuild()       { return build; }
    public String getAppVersion()  { return appVersion; }
    public String getBrowser()     { return browser; }
    public String getOs()          { return os; }
    public String getBaseUrl()     { return baseUrl; }
}
