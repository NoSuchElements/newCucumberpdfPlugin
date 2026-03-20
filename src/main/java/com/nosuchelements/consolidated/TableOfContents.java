package com.nosuchelements.consolidated;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects section-level page number anchors during PDF construction.
 * Can be used to add a TOC page or bookmarks in future iterations.
 */
public class TableOfContents {

    public static class Entry {
        public final String title;
        public final int    pageNumber;

        public Entry(String title, int pageNumber) {
            this.title      = title;
            this.pageNumber = pageNumber;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public void add(String title, int pageNumber) {
        entries.add(new Entry(title, pageNumber));
    }

    public List<Entry> getEntries() { return entries; }
}
