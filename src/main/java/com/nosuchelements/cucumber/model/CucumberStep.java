package com.nosuchelements.cucumber.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single Cucumber step (or hook result) parsed from JSON.
 *
 * Used for both scenario steps and before/after hook entries.
 * When representing a hook there is no keyword or name; only a result
 * (status, duration, error_message) and optional embeddings.
 */
public class CucumberStep {

    private String keyword    = "";
    private String name       = "";
    private String status     = "passed";
    private String errorMessage;
    private long   durationNanos;

    private CucumberDocString       docString;
    private List<CucumberTableRow>  dataTableRows = new ArrayList<>();
    private List<String>            outputLines   = new ArrayList<>();
    private List<String>            embeddings    = new ArrayList<>();   // base64 PNG/JPEG strings

    // -----------------------------------------------------------------------
    // Derived helpers
    // -----------------------------------------------------------------------

    /** Duration in milliseconds (rounded). */
    public long getDurationMillis() {
        return durationNanos / 1_000_000;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public String getKeyword()           { return keyword; }
    public void   setKeyword(String v)   { this.keyword = v != null ? v : ""; }

    public String getName()              { return name; }
    public void   setName(String v)      { this.name = v != null ? v : ""; }

    public String getStatus()            { return status; }
    public void   setStatus(String v)    { this.status = v != null ? v : "passed"; }

    public String getErrorMessage()      { return errorMessage; }
    public void   setErrorMessage(String v) { this.errorMessage = v; }

    public long   getDurationNanos()     { return durationNanos; }
    public void   setDurationNanos(long v) { this.durationNanos = v; }

    public CucumberDocString getDocString()         { return docString; }
    public void setDocString(CucumberDocString ds)  { this.docString = ds; }

    public List<CucumberTableRow> getDataTableRows() { return dataTableRows; }
    public void setDataTableRows(List<CucumberTableRow> rows) {
        this.dataTableRows = rows != null ? rows : new ArrayList<>();
    }

    public List<String> getOutputLines() { return outputLines; }
    public void setOutputLines(List<String> lines) {
        this.outputLines = lines != null ? lines : new ArrayList<>();
    }

    public List<String> getEmbeddings()  { return embeddings; }
    public void setEmbeddings(List<String> emb) {
        this.embeddings = emb != null ? emb : new ArrayList<>();
    }
}
