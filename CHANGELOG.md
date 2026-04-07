# Changelog — Cucumber PDF Reporter

All notable changes are documented here.  
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [1.4.0] — 2026-04-07

### Fixed

- **SC1 — Screenshot / embedding compatibility across all Cucumber JVM versions**
  `CucumberJsonParser.parseEmbeddings()` previously read only `"mime_type"` (with
  underscore), causing screenshots to be silently dropped when the report was produced
  by Cucumber 5.x / 6.x, which writes `"mimetype"` (no underscore) in result-level
  embeddings.  A new private helper `getMimeType(JsonObject)` now checks `"mime_type"`
  first and falls back to `"mimetype"`, covering every location and version in the
  compatibility matrix:

  | JSON location | Cucumber version | API | Field name |
  |---|---|---|---|
  | `step["embeddings"]` | 4.x | `scenario.embed()` in step | `mime_type` |
  | `step["result"]["embeddings"]` | 4 / 5 | result-level | `mime_type` |
  | `step["result"]["embeddings"]` | 5 / 6 | result-level | `mimetype` (no `_`) |
  | `step["after"][n]["embeddings"]` | 7.x | `@AfterStep` | `mime_type` + optional `name` |
  | `scenario["after"][n]["embeddings"]` | 4 / 7 | `@After` | `mime_type` |

  No changes are required in test-project code.  `scenario.attach(bytes, "image/png", name)`
  continues to work unchanged with Cucumber 7.x.

- **SC2 — DocString `content` / `value` field fallback**
  Cucumber 7+ writes DocString text into `"content"`; Cucumber 4 / 5 / 6 use `"value"`.
  The parser now checks `"content"` first and falls back to `"value"` so DocStrings
  render correctly regardless of Cucumber version.

### Changed
- `pom.xml` version bumped to `1.4.0`
- `README.md` — version badge, What's New section, Screenshot & Attachment section,
  Supported Cucumber JSON Formats table, and running-locally command updated to `1.4.0`

---

## [1.3.0] — 2026-04-02

### Fixed

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

## [1.2.0] Completion additions — 2025-06-10

### Added
- **`FailureSummarySection`** — CI triage page inserted immediately after Dashboard,
  showing every FAILED and SKIPPED scenario with full step detail and error messages.
  When all scenarios pass, a green "All scenarios passed" banner is rendered instead.
  Controlled by `displayFailureSummary` (default `true`).
- **`FeaturesSection` — Case ID column** — `tagPrefix` is now fully used; each feature
  row displays its extracted case ID (e.g. `TC-1234`) in an accent-coloured column.
  Features without a matching tag show an em-dash.
- **`ScenariosSection` — Tags column** — each scenario row now shows its first two tags
  in italic muted text. More than two tags are summarised as `+N`.
- **`ConsolidatedPdfGenerator` — 10-arg constructor** adding `displayFailureSummary`.
  Legacy 8-arg and 9-arg constructors kept for backward compatibility.
- **`ConsolidatedCompletionIT`** — 9 integration tests covering all three completion
  items plus section ordering, all-pass banner, And/But keyword rendering in
  FailureSummarySection, and 8-arg constructor backward compatibility.

### Fixed
- Removed dead `pageReg` (PageNumberRegistry) variable from `ConsolidatedPdfGenerator`
- Removed unused `PageNumberRegistry` import from `ConsolidatedPdfGenerator`
- Replaced wasteful `countPages(String)` helper with `doc.getNumberOfPages()`

### Changed
- `ScenariosSection` column layout adjusted to fit Tags column
- `FeaturesSection` column layout adjusted with new Case ID column

---

## [1.2.0] — 2025-06-10

### Added
- **`reportMode` parameter** — `split`, `consolidated`, `both`
- **`DashboardSection`**, **`FeaturesSection`**, **`ScenariosSection`**,
  **`DetailedSection`**, **`TagStatsSection`**, **`ExpandedSection`**
- Two-pass page numbering
- `ConsolidatedPageCursor.PageNumberRegistry`
- New Mojo parameters: `consolidatedReportName`, `reportTitle`, section visibility flags
- `ConsolidatedPdfGeneratorIT` — 6 integration tests

### Changed
- `pom.xml` version bumped to `1.2.0`
- `README.md` fully rewritten

---

## [1.1.6] — 2025-04-xx

### Added
- F-07: Adaptive feature/scenario name font size and two-line wrapping in headers
- F-08: Step name word-wrap across two lines in `DetailedPage`
- F-13: Multi-module JSON consolidation (`consolidate`, `scanRoot`, `cucumberJsonPattern`)
- F-15: Parallel PDF generation (`parallel=true`)
- F-16-config: Configurable tag prefix (`tagPrefix`)
- F-10: Runtime colour overrides via `<colors>` configuration block
- F-06: Visual distinction between `undefined`/`pending` (violet) and `skipped` (amber)

### Fixed
- Screenshots attached in `@After` hooks now correctly discovered via `CucumberScenario.getAllScreenshots()`
- CRLF stack traces no longer cause WinAnsiEncoding crashes in `PdfStyler.sanitise()`

---

## [1.1.3] — 2025-03-xx

### Added
- F-01: And/But keyword visual hierarchy (italic, extra indent)
- F-02: Scenario-level tags in detail page header band
- F-03: Scenario Outline row context bar
- F-04: Configurable output log line limit (`maxOutputLines`)
- F-05: Continuation page context header

---

## [1.1.1] — 2025-02-xx

### Changed
- Removed SummaryPage from default page set
- FeaturePage redesigned: modern slate/indigo colour palette

---

## [1.0.0] — 2024-12-xx

### Added
- Initial release
- One PDF per Cucumber feature from JSON results
- FeaturePage + DetailedPage structure
- Apache PDFBox 2.0.30 baseline
- Basic colour scheme (green/red/amber)
