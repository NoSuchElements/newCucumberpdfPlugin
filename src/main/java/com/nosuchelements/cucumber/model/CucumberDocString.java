package com.nosuchelements.cucumber.model;

/**
 * Represents a DocString block attached to a Cucumber step.
 */
public class CucumberDocString {

    private String content;
    private String contentType;

    public CucumberDocString() {}

    public String getContent()      { return content; }
    public void   setContent(String content)      { this.content = content; }
    public String getContentType()  { return contentType; }
    public void   setContentType(String contentType) { this.contentType = contentType; }
}
