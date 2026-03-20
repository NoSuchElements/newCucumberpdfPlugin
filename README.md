# Cucumber PDF Reporter  v1.2.0

A Maven plugin that generates **professional PDF reports** from Cucumber JSON test results.

## What's New in v1.2.0

| Feature | Description |
|---------|-------------|
| `reportMode` | Three generation modes: `split`, `consolidated`, `both` |
| `consolidated` | Single PDF with Dashboard → Features → Scenarios → Detailed → Expanded, modelled on the [grasshopper7 cucumber-pdf-plugin](https://github.com/grasshopper7/cucumber-pdf-plugin) layout |
| `displayExpanded` | Optional screenshots section (off by default) |
| `reportTitle` | Customise the Dashboard page title |
| `consolidatedReportName` | Output filename for the consolidated PDF |

---

## Report Modes

### `split` (default — original v1.x behaviour)
One PDF per Cucumber feature. Ideal for per-ticket test reports uploaded to qTest, Jira, etc.

### `consolidated`
A **single PDF** covering all features and scenarios in one document.  
Sections (each toggleable):

| Section | Default | Description |
|---------|---------|-------------|
| Dashboard | ✅ | Metric cards, distribution bars, features-at-a-glance table |
| Failure Summary | ✅ | **CI triage page** — every FAILED/SKIPPED scenario with full error. Green all-pass banner when no failures. |
| Features | ✅ | Full feature table with Case ID column, scenario/step counts, and progress bars |
| Scenarios | ✅ | All scenarios grouped by feature, with Tags column and mini progress bars |
| Tag Stats | ✅ | Per-tag pass/fail/skip table, sorted by failure count |
| Detailed | ✅ | Step-by-step breakdown: keyword hierarchy, errors, data tables, docstrings, logs |
| Expanded | ❌ | Screenshots and full attachments (opt-in to keep file size small) |

### `both`
Generates both the consolidated single PDF **and** all per-feature split PDFs in one execution.

---

## Quick Start

### Minimal split (unchanged from v1.x)
```xml
<plugin>
  <groupId>com.nosuchelements</groupId>
  <artifactId>cucumber-pdf-reporter</artifactId>
  <version>1.2.0</version>
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

### Consolidated report
```xml
<configuration>
  <reportMode>consolidated</reportMode>
  <cucumberJsonPattern>**/cucumber*.json</cucumberJsonPattern>
  <reportTitle>Regression Suite — Sprint 42</reportTitle>
  <consolidatedReportName>sprint-42-results.pdf</consolidatedReportName>
  <displayExpanded>true</displayExpanded>
</configuration>
```

### Both modes
```xml
<configuration>
  <reportMode>both</reportMode>
  <cucumberJsonPattern>**/cucumber*.json</cucumberJsonPattern>
  <reportTitle>My Project Full Run</reportTitle>
  <consolidatedReportName>full-report.pdf</consolidatedReportName>
  <tagPrefix>JIRA_</tagPrefix>
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

---

## Full Parameter Reference

### Input discovery

| Parameter | Default | Description |
|-----------|---------|-------------|
| `cucumberJson` | `${project.build.directory}/cucumber.json` | Path to a single JSON results file |
| `cucumberJsonPattern` | — | Ant-style glob to match multiple JSON files (overrides `cucumberJson`) |
| `scanRoot` | `${project.basedir}` | Root for glob scanning. Set to `${project.basedir}/..` for multi-module |

### Report mode

| Parameter | Default | Description |
|-----------|---------|-------------|
| `reportMode` | `split` | `split` \| `consolidated` \| `both` |

### Output

| Parameter | Default | Description |
|-----------|---------|-------------|
| `reportOutputDir` | `${project.build.directory}/cucumber-reports` | Directory for all generated PDFs |
| `consolidatedReportName` | `cucumber-report.pdf` | File name for the consolidated PDF |

### Consolidated sections

| Parameter | Default | Description |
|-----------|---------|-------------|
| `reportTitle` | `Cucumber Test Report` | Title on the Dashboard page |
| `displayDashboard` | `true` | Show Dashboard section |
| `displayFeature` | `true` | Show Features table section |
| `displayScenario` | `true` | Show Scenarios section |
| `displayDetailed` | `true` | Show Detailed steps section |
| `displayExpanded` | `false` | Show Expanded screenshots section |

### Split PDF content

| Parameter | Default | Description |
|-----------|---------|-------------|
| `includeFeaturePage` | `true` | Feature overview page |
| `includeDetailedPages` | `true` | Scenario detail pages |

### Behaviour

| Parameter | Default | Description |
|-----------|---------|-------------|
| `consolidate` | `false` | Deduplicate features by URI (multi-module) |
| `parallel` | `false` | Generate split PDFs in parallel |
| `failOnNoFeatures` | `true` | Fail the build if no JSON files found |
| `skipSplitPdfReporter` | `false` | Skip plugin entirely |
| `verbose` | `false` | Detailed logging |
| `maxOutputLines` | `20` | Max step output / error lines shown |
| `tagPrefix` | `QTEST_TC_` | Tag prefix for case ID extraction (`@QTEST_TC_1234`) |

### Colour customisation

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

All values are 6-character hex RGB strings without `#`. Invalid or absent values keep the default.

---

## Consolidated PDF Layout Detail

```
Page 1 — Dashboard
┌──────────────────────────────────────────────────────────────────┐
│  [Report Title]                                   [PASSED badge] │
│  Generated: 2025-06-10  14:32                                    │
├─────────────────┬──────────────────┬──────────────┬─────────────┤
│  Features       │  Scenarios       │  Steps       │  Duration   │
│  12             │  47              │  312          │  4m 32s    │
│  10 passed      │  43 passed       │  298 passed   │            │
├──────────────────────────────────────────────────────────────────┤
│  Scenarios Distribution  ████████████████████░░░░ 91%           │
│  ● Passed 43   ● Failed 2   ● Skipped 2                         │
├──────────────────────────────────────────────────────────────────┤
│  Steps Distribution  ███████████████████████░░░░                │
│  ● Passed 298   ● Failed 6   ● Skipped 8                        │
├──────────────────────────────────────────────────────────────────┤
│  Features at a Glance                                            │
│  # │ Feature Name     │ Status  │ Scen │ Progress │ Duration   │
│  1 │ User Auth        │ PASSED  │ 5/5  │ ████████ │  2.3s      │
│  2 │ Shopping Cart    │ FAILED  │ 3/4  │ ████░░░░ │  6.7s      │
│  …                                                               │
└──────────────────────────────────────────────────────────────────┘

Pages N+ — Features
  Full table with URI, all stats, step progress bars.

Pages N+ — Scenarios
  Grouped by feature with mini progress bars and duration.

Pages N+ — Detailed
  Per-scenario step-by-step view with:
    • Keyword hierarchy (Given/When/Then vs And/But)
    • Red error blocks with full stack trace
    • Data tables
    • DocStrings
    • Step output logs

Pages N+ — Expanded  (displayExpanded=true)
  Screenshots embedded at full width, grouped by scenario.
```

---

## Screenshot Support

Screenshots attached in `@Before`, `@After`, step-level, or `@AfterStep` hooks are all captured.
In `split` mode they appear in the scenario detail pages.
In `consolidated` mode they appear in the **Expanded** section (requires `displayExpanded=true`).

```java
// Any of these patterns are supported:
scenario.attach(screenshotBytes, "image/png", "screenshot");  // Cucumber 7+
((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES); // Selenium @After
```

---

## Supported Cucumber JSON Formats

- Cucumber-JVM 4.x, 5.x, 6.x, 7.x
- Both `mime_type` and `mimetype` embedding fields
- Both `content` (Cucumber 7+) and `value` (older) DocString fields
- Background steps (paired to following scenarios)
- Scenario Outline rows (with row context bar in split mode)
- `@AfterStep` hook embeddings (SpringBDDAutomationFramework compatible)

---

## Building from Source

```bash
mvn clean install -Dmaven.test.skip=true   # skip ITs for local build
mvn clean verify                           # run all tests
mvn clean deploy -Prelease                 # release to Maven Central
```
