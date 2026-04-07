# Cucumber PDF Reporter — v1.4.0

[![Maven Central](https://img.shields.io/maven-central/v/com.nosuchelements/cucumber-pdf-reporter.svg)](https://search.maven.org/artifact/com.nosuchelements/cucumber-pdf-reporter)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java 11+](https://img.shields.io/badge/Java-11%2B-blue.svg)](https://adoptium.net/)

A Maven plugin that generates **professional, production-grade PDF reports** from Cucumber JSON test results.  
Supports three modes — `split` (one PDF per feature), `consolidated` (single multi-section PDF), and `both`.

---

## What's New in v1.4.0

v1.4.0 is a **screenshot compatibility release** that fixes silent embedding loss affecting
users running Cucumber 5.x or 6.x, and adds a DocString fallback for older versions.

### Bug Fixes

| ID | Component | Summary |
|----|-----------|-----------------------------------------------------------|
| SC1 | `CucumberJsonParser` | **Screenshots / embeddings now captured across ALL Cucumber JVM versions (4.x – 7.x).** Previously only `"mime_type"` (with underscore) was read; Cucumber 5.x/6.x result-level embeddings use `"mimetype"` (no underscore) and were silently dropped. A new `getMimeType()` helper accepts both field variants. |
| SC2 | `CucumberJsonParser` | **DocString content now parsed for all Cucumber versions.** Cucumber 7+ writes the field as `"content"`; Cucumber 4/5/6 use `"value"`. The parser now checks `"content"` first and falls back to `"value"`. |

### Embedding Compatibility Matrix (fully covered as of v1.4.0)

| JSON location | Cucumber version | API | Field name |
|---|---|---|---|
| `step["embeddings"]` | 4.x | `scenario.embed()` in step | `mime_type` |
| `step["result"]["embeddings"]` | 4 / 5 | result-level | `mime_type` |
| `step["result"]["embeddings"]` | 5 / 6 | result-level | `mimetype` (no `_`) |
| `step["after"][n]["embeddings"]` | 7.x | `@AfterStep` | `mime_type` + optional `name` |
| `scenario["after"][n]["embeddings"]` | 4 / 7 | `@After` | `mime_type` |

No changes required to test-project code — `scenario.attach(bytes, "image/png", name)` works unchanged.

---

## What's New in v1.3.0

v1.3.0 is a **comprehensive quality release** that resolves every known rendering bug across all 8 report sections,
introduces several long-requested enhancements, and adds production hardening throughout.

### Bug Fixes

| ID | Section | Summary |
|----|---------|-----------------------------------------------------------|
| D1 | Dashboard | Overflow pages (>14 features) now redraw the `#F5F7FA` page background — previously pages were white |
| D2 | Dashboard | Section footer is now stamped on the **first** Dashboard page, not the current (overflow) page |
| D5 | Detailed | `@Before` hook output logs are now rendered (were silently dropped) |
| D6 | Detailed | Scenario header duration label uses precise font-metric width instead of character-count approximation — no more overlap |
| D7 | Detailed | Background step **errors and screenshots** now render inline, matching the Failure Summary treatment |
| F1 | Failure Summary | SKIPPED step screenshots now guarded — only rendered for truly failed steps |
| F2 | Failure Summary | Section subtitle correctly shows `N failing / M skipped` instead of always saying `N failing` |
| F4 | Features | Windows-style `file:///C:/` URI prefix is now fully stripped (`replaceFirst("^file:/{1,3}", "")`) |
| F5 | Features | Footer `hLine` and summary text no longer overlap due to premature `cur.advance()` before stream opens |
| S1 | Scenarios | Tag overflow-count suffix (e.g. `+3`) no longer clipped by column truncation |
| T1 | Tag Statistics | Empty-tag path now uses a proper `ConsolidatedPageCursor` — page footer and page-number registry are consistent |
| E1 | Expanded | Scenario header now reserves space for header **plus at least one image** before drawing — orphan headers eliminated |
| SP1 | Split | `"Background"` label is now drawn before background steps in split-mode PDFs |
| SP2 | Split | `generateFilename` now accepts a `tagPrefix` parameter and scans `feature.getTags()` when `qtestCaseId` is null |
| CC1 | All sections | `ContentBlockRenderer.renderErrorBlock()` now uses the configured `maxOutputLines` (pass `-1`) instead of hardcoded literals |

### Enhancements

| ID | Section | Summary |
|----|---------|-----------------------------------------------------------|
| D3 | Dashboard / Features | `extractCaseId()` deduplicated into shared `FeatureUtils` utility class |
| D4 | All footers | Version string centralised in `PluginVersion.FULL` — no more hardcoded `"v1.2.0"` in three places |
| E2 | Expanded | Non-image MIME-type embeddings (text/plain, application/json, etc.) render a labelled placeholder box instead of crashing on `createFromByteArray` |
| F3 | Failure Summary | Failed/skipped steps now show **both** the status label and step duration in the right column |
| S2 | Scenarios | Scenario Outline example rows show `—` in the tag column instead of repeating the same overflow tags on every row |
| T2 | Tag Statistics | `@QTEST_TC_*` tags (matching `tagPrefix`) are sorted into their own group and rendered after functional tags |
| SP3 | Split | Feature-level tags (e.g. `@QTEST_TC_1001`, `@smoke`) now shown in the split-mode feature summary page |
| CC2 | All sections | Sanitisation pipeline unified: `ContentBlockRenderer.sanitiseLogLine()` (handles `\t`) feeds into `PdfStyler.sanitise()` (handles remaining control chars < 32) |
| CC3 | All generators | PDF document metadata now set on every generated file: `title`, `producer` (`Cucumber PDF Reporter v1.3.0`), and `creationDate` |

---

## Report Modes

### `split` (default — original v1.x behaviour)

Generates **one PDF per Cucumber feature file**. Ideal for per-ticket test reports uploaded to qTest, Jira, Xray, or any test management tool.

Filename format:
- Without tag prefix: `login-feature.pdf`
- With `@QTEST_TC_` tag: `login-feature@QTEST_TC_1001.pdf`
- With custom prefix: configured via `tagPrefix`

### `consolidated`

A **single PDF** covering all features and scenarios in one document with a navigable Table of Contents.  
Sections (each independently toggleable):

| Section | Parameter | Default | Description |
|---------|-----------|---------|-------------|
| Dashboard | `displayDashboard` | `true` | Metric cards, pass/fail/skip distribution bars, Features-at-a-Glance table |
| Failure Summary | `displayFailureSummary` | `true` | CI triage page — every FAILED/SKIPPED scenario with full stack trace. Green all-pass banner when no failures |
| Features | `displayFeature` | `true` | Full feature table with Case ID column, scenario/step counts, URI, and progress bars |
| Scenarios | `displayScenario` | `true` | All scenarios grouped by feature, with tags column and mini progress bars |
| Tag Statistics | `displayTagStats` | `true` | Per-tag pass/fail/skip table sorted by failure count, with `@QTEST_TC_*` tags grouped separately |
| Detailed Steps | `displayDetailed` | `true` | Full step-by-step breakdown: keyword hierarchy, errors, data tables, DocStrings, output logs, background steps |
| Expanded / Attachments | `displayExpanded` | `false` | Screenshots and non-image attachments inline, grouped by scenario (opt-in to manage file size) |

### `both`

Runs `split` and `consolidated` in the same Maven execution, writing all per-feature PDFs **and** the consolidated PDF to `reportOutputDir`.

---

## Quick Start

### Minimal split (unchanged from v1.x)

```xml
<plugin>
  <groupId>com.nosuchelements</groupId>
  <artifactId>cucumber-pdf-reporter</artifactId>
  <version>1.4.0</version>
  <executions>
    <execution>
      <id>generate-pdf-reports</id>
      <phase>post-integration-test</phase>
      <goals><goal>generate-pdfs</goal></goals>
    </execution>
  </executions>
  <configuration>
    <cucumberJson>${project.build.directory}/cucumber.json</cucumberJson>
    <reportOutputDir>${project.build.directory}/cucumber-reports</reportOutputDir>
  </configuration>
</plugin>
```

### Consolidated report (all sections)

```xml
<configuration>
  <reportMode>consolidated</reportMode>
  <cucumberJsonPattern>**/cucumber*.json</cucumberJsonPattern>
  <reportTitle>Regression Suite — Sprint 42</reportTitle>
  <consolidatedReportName>sprint-42-results.pdf</consolidatedReportName>
  <displayExpanded>true</displayExpanded>
  <maxOutputLines>30</maxOutputLines>
</configuration>
```

### Both modes together

```xml
<configuration>
  <reportMode>both</reportMode>
  <cucumberJsonPattern>**/cucumber*.json</cucumberJsonPattern>
  <reportTitle>My Project — Full Run</reportTitle>
  <consolidatedReportName>full-report.pdf</consolidatedReportName>
  <tagPrefix>QTEST_TC_</tagPrefix>
</configuration>
```

### Multi-module project consolidation

```xml
<configuration>
  <reportMode>consolidated</reportMode>
  <cucumberJsonPattern>**/cucumber*.json</cucumberJsonPattern>
  <scanRoot>${project.basedir}/..</scanRoot>
  <consolidate>true</consolidate>
  <reportOutputDir>${project.basedir}/target/cucumber-reports</reportOutputDir>
</configuration>
```

---

## Full Parameter Reference

### Input Discovery

| Parameter | Default | Description |
|-----------|---------|-------------|
| `cucumberJson` | `${project.build.directory}/cucumber.json` | Path to a single Cucumber JSON results file |
| `cucumberJsonPattern` | — | Ant-style glob to match multiple JSON files across the project tree (overrides `cucumberJson`) |
| `scanRoot` | `${project.basedir}` | Root directory for glob scanning. Set to `${project.basedir}/..` for multi-module builds |

### Report Mode

| Parameter | Default | Allowed Values | Description |
|-----------|---------|----------------|-------------|
| `reportMode` | `split` | `split` \| `consolidated` \| `both` | Controls which report type(s) are generated |

### Output

| Parameter | Default | Description |
|-----------|---------|-------------|
| `reportOutputDir` | `${project.build.directory}/cucumber-reports` | Directory where all generated PDFs are written |
| `consolidatedReportName` | `cucumber-report.pdf` | File name of the consolidated single PDF |

### Consolidated Sections

| Parameter | Default | Description |
|-----------|---------|-------------|
| `reportTitle` | `Cucumber Test Report` | Title text shown on the Dashboard page header |
| `displayDashboard` | `true` | Render the Dashboard section (metric cards + features-at-a-glance) |
| `displayFailureSummary` | `true` | Render the Failure Summary CI triage section |
| `displayFeature` | `true` | Render the Features table section |
| `displayScenario` | `true` | Render the Scenarios grouped table section |
| `displayTagStats` | `true` | Render the Tag Statistics section |
| `displayDetailed` | `true` | Render the Detailed step-by-step section |
| `displayExpanded` | `false` | Render the Expanded screenshots / attachments section |

### Split PDF Content

| Parameter | Default | Description |
|-----------|---------|-------------|
| `includeFeaturePage` | `true` | Include a feature overview summary page in each split PDF |
| `includeDetailedPages` | `true` | Include per-scenario step detail pages in each split PDF |

### Behaviour

| Parameter | Default | Description |
|-----------|---------|-------------|
| `consolidate` | `false` | Deduplicate features by URI when scanning across modules (multi-module builds) |
| `parallel` | `false` | Generate split PDFs concurrently using a thread pool |
| `failOnNoFeatures` | `true` | Fail the Maven build if no Cucumber JSON files are found |
| `skipSplitPdfReporter` | `false` | Skip the plugin entirely (useful for CI environment toggling) |
| `verbose` | `false` | Enable detailed per-feature and per-section logging |
| `maxOutputLines` | `20` | Maximum lines of step output / error stack trace shown per block. Pass `-1` for unlimited |
| `tagPrefix` | `QTEST_TC_` | Tag prefix used for Case ID extraction and split PDF filename suffixing (e.g. `@QTEST_TC_1234`) |

### Colour Customisation

All colours are 6-character hex RGB strings **without** `#`. Invalid or absent values silently fall back to the built-in defaults.

```xml
<configuration>
  <colors>
    <headerBackground>1E3A5F</headerBackground>
    <accent>0052CC</accent>
    <passed>00875A</passed>
    <failed>DE350B</failed>
    <skipped>FF8B00</skipped>
    <pageBackground>F4F5F7</pageBackground>
    <cardBackground>FFFFFF</cardBackground>
  </colors>
</configuration>
```

---

## Consolidated PDF — Section Layout

```
┌─────────────────────────────────────────────────────────────────┐
│  Page 1 — Dashboard                                             │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Report Title                         [PASSED / FAILED]  │  │
│  │  Generated: 2026-04-07  11:41                            │  │
│  ├──────────────┬──────────────┬─────────────┬─────────────┤  │
│  │  Features    │  Scenarios   │  Steps      │  Duration   │  │
│  │  9           │  26          │  112        │  4m 32s     │  │
│  ├──────────────────────────────────────────────────────────┤  │
│  │  Scenarios  ████████████████████░░░░  91%                │  │
│  │  ● Passed 22  ● Failed 7  ● Skipped 5                   │  │
│  ├──────────────────────────────────────────────────────────┤  │
│  │  Features at a Glance                                    │  │
│  │  # │ Feature Name        │ CaseID      │ Status  │ ...  │  │
│  │  1 │ DataTable+DocString │ QTEST_1201  │ PASSED  │ ...  │  │
│  │  2 │ Exception           │ QTEST_1202  │ FAILED  │ ...  │  │
│  │  3 │ Hook & Step Fail    │ QTEST_1203  │ FAILED  │ ...  │  │
│  │  … │ …                   │ …           │ …       │ …   │  │
│  └──────────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────────┤
│  Page N — Failure Summary (CI Triage)                           │
│  Every FAILED / SKIPPED scenario listed with:                   │
│    • Before-hook errors + output logs                           │
│    • Each failing step: keyword, name, status label, duration   │
│    • Full stack trace (up to maxOutputLines)                    │
│    • After-hook errors + output logs                            │
│    • Screenshots for failed steps only                          │
│  ✅ All-pass green banner when zero failures                     │
├─────────────────────────────────────────────────────────────────┤
│  Page N — Features Table                                        │
│  One row per feature: #, name, URI (no file:/// prefix),        │
│  Case ID, status badge, scenario fraction, step progress bar,   │
│  duration.                                                      │
├─────────────────────────────────────────────────────────────────┤
│  Page N — Scenarios                                             │
│  Grouped by feature. Columns: #, name, tags (truncated per tag, │
│  not the assembled line), status, step fraction, bar, duration. │
│  Scenario Outline example rows show — in the tag column.        │
├─────────────────────────────────────────────────────────────────┤
│  Page N — Tag Statistics                                        │
│  Sorted: functional tags by failure count desc, then            │
│  @QTEST_TC_* tags in their own group below.                     │
│  Columns: tag, total, passed, failed, skipped, progress bar.    │
├─────────────────────────────────────────────────────────────────┤
│  Page N — Detailed Steps                                        │
│  Per scenario: background label + steps, before-hook logs,      │
│  Given/When/Then keyword hierarchy, And/But continuation dots,  │
│  red error blocks, data tables, DocStrings, output logs,        │
│  background step errors and screenshots (fixed in v1.3.0).      │
├─────────────────────────────────────────────────────────────────┤
│  Page N — Expanded / Attachments  (displayExpanded=true)        │
│  All embeddings in source order per scenario.                   │
│  image/png + image/jpeg → full-width embedded image.            │
│  Other MIME types → labelled placeholder (fixed in v1.3.0):     │
│    ┌──────────────────────────────────────┐                     │
│    │  📎  Attachment: text/plain  1.2 KB  │                     │
│    └──────────────────────────────────────┘                     │
└─────────────────────────────────────────────────────────────────┘
```

---

## Split PDF — Per-Feature Layout

Each feature produces one PDF named `<feature-slug>[@<tagPrefix><caseId>].pdf`.

```
┌─────────────────────────────────────────────────────────────────┐
│  Page 1 — Feature Summary  (includeFeaturePage=true)            │
│  Feature name, URI, tags including @QTEST_TC_* (fixed v1.3.0),  │
│  overall status, scenario count, step count, duration,          │
│  per-scenario summary list with status badges.                  │
├─────────────────────────────────────────────────────────────────┤
│  Page N — Scenario Detail  (includeDetailedPages=true)          │
│  Per scenario:                                                   │
│    • "Background" label before background steps (fixed v1.3.0)  │
│    • Each step: keyword, name, status dot, duration             │
│    • Error block with stack trace                               │
│    • Embedded screenshots (all Cucumber versions, fixed v1.4.0) │
└─────────────────────────────────────────────────────────────────┘
```

### Split PDF Filename Examples

| Feature URI | `tagPrefix` | Output filename |
|-------------|-------------|-----------------|
| `datatable-docstring.feature` | `QTEST_TC_` (default) | `datatable-docstring@QTEST_TC_1201.pdf` |
| `exceptions.feature` | `QTEST_TC_` | `exceptions@QTEST_TC_1202.pdf` |
| `failure.feature` | `QTEST_TC_` | `failure@QTEST_TC_1203.pdf` |
| `lengthynames.feature` | `QTEST_TC_` | `lengthynames@QTEST_TC_1204.pdf` |
| `notag.feature` | `QTEST_TC_` | `notag.pdf` |
| `exceptions.feature` | `JIRA_` | `exceptions.pdf` (no matching tag) |

---

## Screenshot & Attachment Support

Embeddings attached at any lifecycle point are captured across **all Cucumber JVM versions** (fixed in v1.4.0):

```java
// Cucumber 7+ (recommended) — writes "mimetype" field
scenario.attach(screenshotBytes, "image/png", "screenshot");

// Cucumber 7+ Selenium @After hook
byte[] shot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
scenario.attach(shot, "image/png", "page-screenshot");

// Cucumber 4.x (deprecated) — writes "mime_type" field
scenario.embed(screenshotBytes, "image/png");  // still parsed correctly

// Non-image attachments — rendered as placeholder box (v1.3.0+)
scenario.attach(logContent.getBytes(), "text/plain", "step-log");
```

**Where they appear:**

| Mode | Location |
|------|----------|
| `split` | Inline in each scenario detail page |
| `consolidated` | Expanded section only (`displayExpanded=true`) |

---

## Supported Cucumber JSON Formats

- **Cucumber-JVM** 4.x, 5.x, 6.x, 7.x
- **Both** `mime_type` **and** `mimetype` embedding field variants (fixed v1.4.0)
- **Both** `content` (Cucumber 7+) **and** `value` (older) DocString fields (fixed v1.4.0)
- Background steps (correctly paired to following scenarios)
- Scenario Outline example rows (with row index context)
- `@AfterStep` hook embeddings (SpringBDDAutomationFramework compatible)
- Windows `file:///C:/` URIs (normalised in v1.3.0)
- Long feature/scenario names (truncated safely with `...`)
- Features with no tags (filename generated from URI slug)

---

## Architecture Overview

```
com.nosuchelements
├── maven/
│   └── GeneratePdfsMojo.java          Maven Mojo (@goal generate-pdfs)
├── cucumber/
│   ├── CucumberJsonParser.java        Gson-based JSON parser (SC1/SC2 fixed in v1.4.0)
│   └── model/                         CucumberFeature, CucumberScenario, CucumberStep
├── pdf/
│   ├── PdfStyler.java                 Font loading, text drawing, shape helpers
│   ├── ColorScheme.java               Named colour constants + status → colour mapping
│   └── PdfBoxUtils.java               PDFBox utility wrappers
├── consolidated/
│   ├── ConsolidatedPdfGenerator.java  Orchestrates all 7 sections → single PDF
│   ├── ConsolidatedPageCursor.java    Tracks y-position and page overflow
│   ├── ContentBlockRenderer.java      Shared: error blocks, data tables, DocStrings,
│   │                                  log lines, screenshot groups (CC1/CC2 fixed)
│   ├── FeatureUtils.java              Shared: extractCaseId(), normaliseUri() (D3 fixed)
│   ├── PluginVersion.java             FULL = "Cucumber PDF Reporter v1.4.0" (D4 fixed)
│   ├── ReportStats.java               Aggregated pass/fail/skip counts
│   ├── SectionHeader.java             Reusable section banner renderer
│   ├── TableOfContents.java           Page-number registry for TOC
│   └── sections/
│       ├── DashboardSection.java      (D1, D2 fixed)
│       ├── FailureSummarySection.java (F1, F2, F3 fixed)
│       ├── FeaturesSection.java       (D3 shared, F4, F5 fixed)
│       ├── ScenariosSection.java      (S1, S2 fixed)
│       ├── TagStatsSection.java       (T1, T2 fixed)
│       ├── DetailedSection.java       (D5, D6, D7 fixed)
│       └── ExpandedSection.java       (E1, E2 fixed)
└── split/
    └── FeaturePdfGenerator.java       Per-feature PDF (SP1, SP2, SP3 fixed)
```

---

## Building from Source

```bash
# Build without running integration tests
mvn clean install -Dmaven.test.skip=true

# Full build with tests
mvn clean verify

# Release to Maven Central (requires GPG key + OSSRH credentials)
mvn clean deploy -Prelease
```

### Running the Plugin Locally Against Your JSON

```bash
mvn com.nosuchelements:cucumber-pdf-reporter:1.4.0:generate-pdfs \
  -DcucumberJson=target/cucumber.json \
  -DreportMode=both \
  -DreportOutputDir=target/pdf-reports \
  -DdisplayExpanded=true \
  -DtagPrefix=QTEST_TC_
```

---

## Migration Guide — v1.3.0 → v1.4.0

All existing configuration is **fully backwards compatible**. Only the version number needs updating:

```xml
<!-- Before -->
<version>1.3.0</version>

<!-- After -->
<version>1.4.0</version>
```

No parameters were added, removed, or renamed. Screenshots that were silently missing in
Cucumber 5.x / 6.x reports will now appear automatically after upgrading.

---

## Changelog

### v1.4.0 (2026-04-07)
- **SC1** — Screenshot / embedding compatibility across ALL Cucumber JVM versions (4.x–7.x).
  Both `mime_type` and `mimetype` field variants now resolved via `getMimeType()` helper.
- **SC2** — DocString `content` / `value` field fallback added for Cucumber 4/5/6 compatibility.
- `pom.xml` version `1.4.0`, README and CHANGELOG updated.

### v1.3.0 (2026-04-02)
- 15 bug fixes across Dashboard, Failure Summary, Features, Scenarios, Tag Statistics, Detailed, Expanded, and Split sections
- 9 enhancements including unified sanitisation, PDF metadata, non-image placeholders, `FeatureUtils` DRY refactor

### v1.2.0
- Added `consolidated` and `both` report modes
- 7-section consolidated PDF

### v1.1.x
- Split mode stabilisation, `@AfterStep` embedding support

### v1.0.0
- Initial release: split mode, one PDF per feature

---

## License

[MIT](https://opensource.org/licenses/MIT) © NoSuchElements
