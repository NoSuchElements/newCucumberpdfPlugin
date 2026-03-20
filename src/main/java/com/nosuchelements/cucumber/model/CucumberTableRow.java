package com.nosuchelements.cucumber.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single row in a Cucumber data table.
 */
public class CucumberTableRow {

    private List<String> cells = new ArrayList<>();

    public CucumberTableRow() {}

    public CucumberTableRow(List<String> cells) {
        this.cells = cells != null ? cells : new ArrayList<>();
    }

    public List<String> getCells() { return cells; }
    public void setCells(List<String> cells) { this.cells = cells != null ? cells : new ArrayList<>(); }
}
