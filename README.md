# Cucumber PDF Reporter  v1.5.0

A Maven plugin that generates **professional PDF reports** from Cucumber JSON test results.

## What's New in v1.5.0

| Feature | Description |
|---------|-------------|
| `<metadata>` block | Environment / build info block on Dashboard page |
| `displaySlowTests` | Top-N slowest scenarios section for performance triage |
| `slowTestTopN` | How many slow tests to list (default 15) |
| PDF document properties | Title, subject, creator set on all generated PDFs |
| Split mode: DataTable | DataTables now rendered in per-feature PDFs |
| Split mode: DocString | DocStrings now rendered in per-feature PDFs |
| Split mode: output logs | Step output now rendered in per-feature PDFs |
| Split mode: background | Background steps now rendered in per-feature PDFs |
| Split mode: scenario tags | Tags now shown below the scenario header band |
| Step spacing fix | Breathing room between steps (LH 14→15pt, +2pt inter-step gap) |
| Adaptive font sizing | Long feature/scenario names auto-shrink to fit header bands |
| Version consistency | All footers and banner unified to v1.5.0 |

---

## Report Modes

### `split` (default)
One PDF per Cucumber feature. Each PDF contains:
- Feature summary page (stats, progress bar, scenario list)
- One detail page per scenario, with:
  - Scenario header (name, status, duration, tags)
  - Background steps (if any)
  - Step rows with keyword, name, duration
  - Error blocks with full CRLF-safe stack traces
  - DataTable blocks
  - DocString blocks
  - Step output logs
  - Screenshots (step and hook embeddings)
  - Before/After hook errors

### `consolidated`
A **single PDF** with toggleable sections:

| Section | Param | Default | Description |
|---------|-------|---------|-------------|
| Dashboard | `displayDashboard` | ✅ | Metrics, distribution bars, at-a-glance table, metadata |
| Failure Summary | `displayFailureSummary` | ✅ | CI triage: every failing scenario with errors + screenshots |
| Slow Tests | `displaySlowTests` | ❌ | Top-N slowest scenarios sorted by duration |
| Features | `displayFeature` | ✅ | Full feature table with Case ID column |
| Scenarios | `displayScenario` | ✅ | All scenarios with Tags column and mini bars |
| Tag Stats | `displayTagStats` | ✅ | Per-tag pass/fail/skip breakdown |
| Detailed | `displayDetailed` | ✅ | Step-by-step: errors, tables, docstrings, screenshots |
| Expanded | `displayExpanded` | ❌ | Full-width screenshots only (keep file size small) |

### `both`
Generates both the consolidated single PDF **and** all per-feature split PDFs in one run.

---

## Quick Start

### Minimal split (unchanged from v1.x)
```xml
<plugin>
  <groupId>com.nosuchelements</groupId>
  <artifactId>cucumber-pdf-reporter</artifactId>
  <version>1.5.0</version>
  <executions>
    <execution>
      <goals><goal>generate-pdfs</goal></goals>
      <phase>post-integration-test</phase>
    </execution>
  </executions>
  <configuration>
    <cucumberJson>${project.build.directory}/cucumber.json</cucumberJson>
    <reportOutputDir>${project.build.directory}/cucumber-reports</reportOutputDir>
  </configuration>
</plugin>
```

### Full consolidated report with metadata
```xml
<configuration>
  <reportMode>consolidated</reportMode>
  <cucumberJsonPattern>**/cucumber*.json</cucumberJsonPattern>
  <reportTitle>Sprint 42 — Regression Suite</reportTitle>
  <consolidatedReportName>sprint-42.pdf</consolidatedReportName>
  <displaySlowTests>true</displaySlowTests>
  <slowTestTopN>20</slowTestTopN>
  <displayExpanded>true</displayExpanded>
  <metadata>
    <environment>QA</environment>
    <branch>${env.GIT_BRANCH}</branch>
    <build>${env.BUILD_NUMBER}</build>
    <appVersion>2.14.0</appVersion>
    <browser>Chrome 124</browser>
    <baseUrl>https://qa.example.com</baseUrl>
  </metadata>
</configuration>
```

### Multi-module consolidation
```xml
<configuration>
  <reportMode>consolidated</reportMode>
  <cucumberJsonPattern>**/cucumber*.json</cucumberJsonPattern>
  <scanRoot>${project.basedir}/..</scanRoot>
  <consolidate>true</consolidate>
  <reportOutputDir>${project.basedir}/target/cucumber-reports</reportOutputDir>
</configuration>
```

### Both modes with JIRA tag prefix
```xml
<configuration>
  <reportMode>both</reportMode>
  <tagPrefix>JIRA_</tagPrefix>
  <reportTitle>My Project Full Run</reportTitle>
</configuration>
```

---

## Full Parameter Reference

### Input discovery

| Parameter | Default | Description |
|-----------|---------|-------------|
| `cucumberJson` | `${project.build.directory}/cucumber.json` | Path to a single JSON results file |
| `cucumberJsonPattern` | — | Ant glob to match multiple JSON files (overrides `cucumberJson`) |
| `scanRoot` | `${project.basedir}` | Root for glob scanning. Set to `../` for multi-module |

### Report mode

| Parameter | Default | Description |
|-----------|---------|-------------|
| `reportMode` | `split` | `split` \| `consolidated` \| `both` |

### Output

| Parameter | Default | Description |
|-----------|---------|-------------|
| `reportOutputDir` | `${project.build.directory}/cucumber-reports` | Output directory |
| `consolidatedReportName` | `cucumber-report.pdf` | Filename for the consolidated PDF |

### Consolidated sections

| Parameter | Default | Description |
|-----------|---------|-------------|
| `reportTitle` | `Cucumber Test Report` | Dashboard page title |
| `displayDashboard` | `true` | Dashboard section |
| `displayFailureSummary` | `true` | Failure Summary CI triage section |
| `displaySlowTests` | `false` | Slow Tests top-N section |
| `slowTestTopN` | `15` | Number of slow scenarios to list (max 50) |
| `displayFeature` | `true` | Features table |
| `displayScenario` | `true` | Scenarios table |
| `displayTagStats` | `true` | Tag Statistics table |
| `displayDetailed` | `true` | Step-by-step Detailed section |
| `displayExpanded` | `false` | Expanded screenshots section |

### Environment metadata (v1.5.0)

```xml
<metadata>
  <environment>string</environment>   <!-- e.g. QA, STAGING, PROD -->
  <branch>string</branch>             <!-- e.g. ${env.GIT_BRANCH} -->
  <build>string</build>               <!-- e.g. ${env.BUILD_NUMBER} -->
  <appVersion>string</appVersion>     <!-- e.g. 2.14.0-rc3 -->
  <browser>string</browser>           <!-- e.g. Chrome 124 -->
  <os>string</os>                     <!-- e.g. Linux x86_64 -->
  <baseUrl>string</baseUrl>           <!-- e.g. https://qa.example.com -->
</metadata>
```

Any of these fields may be omitted. The block only appears on the Dashboard when at least one field is set.

### Split PDF content

| Parameter | Default | Description |
|-----------|---------|-------------|
| `includeFeaturePage` | `true` | Feature overview page |
| `includeDetailedPages` | `true` | Scenario detail pages |

### Behaviour

| Parameter | Default | Description |
|-----------|---------|-------------|
| `consolidate` | `false` | Deduplicate features by URI (multi-module runs) |
| `parallel` | `false` | Generate split PDFs in parallel |
| `failOnNoFeatures` | `true` | Fail the build if no JSON files found |
| `skipSplitPdfReporter` | `false` | Skip plugin entirely |
| `verbose` | `false` | Detailed per-file logging |
| `maxOutputLines` | `20` | Max lines shown per error block / output log |
| `tagPrefix` | `QTEST_TC_` | Tag prefix for case ID extraction |

### Colour customisation

```xml
<colors>
  <header>1E3A5F</header>     <!-- Header band background -->
  <accent>2E75B6</accent>     <!-- Keyword / link colour -->
  <passed>388E3C</passed>     <!-- Pass colour -->
  <failed>C62828</failed>     <!-- Fail colour -->
  <skipped>E65100</skipped>   <!-- Skip/pending colour -->
  <rowAlt>EBF2FA</rowAlt>     <!-- Alternating row tint -->
</colors>
```

All values are 6-character hex RGB without `#`. Invalid values silently use the default.

---

## Screenshot Support

Screenshots are collected from `@Before`, `@After`, step-level, and `@AfterStep` hooks.

```java
// Cucumber 7+ — attach to scenario
scenario.attach(screenshotBytes, "image/png", "failure-screenshot");

// Classic pattern — any mime type starting with "image/" is captured
((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
```

In **split mode**, screenshots appear inline in the scenario detail page immediately below the step that captured them.

In **consolidated mode**, screenshots appear in both the `Detailed` section (inline per-step) and the `Expanded` section (full-page gallery, requires `displayExpanded=true`).

---

## Supported JSON Formats

- Cucumber-JVM 4.x, 5.x, 6.x, 7.x
- Background steps (paired to following scenarios)
- Before/After hook errors and embeddings
- DataTable rows (`rows[]`)
- DocString blocks (`doc_string`)
- Step output lines (`output[]`)
- Both `mime_type` and `mimetype` embedding fields
- CRLF (`\r\n`) and LF (`\n`) error message line endings
- Tab-prefixed Java stack frames (`\tat ...`)

---

## Building

```bash
mvn clean install -Dmaven.test.skip=true   # skip ITs
mvn clean verify                           # run all tests
mvn clean deploy -Prelease                 # release to Maven Central
```
