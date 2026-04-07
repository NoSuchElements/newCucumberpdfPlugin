# Changelog — Cucumber PDF Reporter

All notable changes are documented here.  
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [1.4.1] — 2026-04-07

### Fixed — Step / Screenshot / Error spacing & padding

All sections that render step detail (Detailed, Expanded, Failure Summary) previously
had no breathing room between a step's separator line and the content blocks below it
(errors, log output, data tables, DocStrings, screenshots). Elements appeared
immediately adjacent, making the PDF visually dense and hard to scan.

| ID | Component | Change |
|----|-----------|--------|
| SP-1 | `ContentBlockRenderer.renderErrorBlock` | Added `BLOCK_GAP_BEFORE = 5pt` before every error block |
| SP-1 | `ContentBlockRenderer.renderLogs` | Added `BLOCK_GAP_BEFORE = 5pt` before every log block |
| SP-1 | `ContentBlockRenderer.renderDataTable` | Added `BLOCK_GAP_BEFORE = 5pt` before every data table |
| SP-1 | `ContentBlockRenderer.renderDocString` | Added `BLOCK_GAP_BEFORE = 5pt` before every DocString block |
| SP-2 | `ContentBlockRenderer.renderScreenshotGroup` | Added `SCREENSHOT_GAP_BEFORE = 8pt` before the first screenshot (or its label) |
| SP-3 | `ContentBlockRenderer.renderScreenshotGroup` | Inter-screenshot gap increased from hard-coded `6pt` → `INTER_SCREENSHOT_GAP = 10pt` |
| SP-4 | `ContentBlockRenderer.renderSingleScreenshot` | Card bottom padding increased `12pt` → `16pt`; `ensureSpace` updated accordingly |
| SP-5 | `DetailedSection.renderStep` | Post-step trailing gap `4pt` → `STEP_TRAIL_GAP = 8pt` |
| SP-6 | `ExpandedSection.renderStep` | Post-step trailing gap `4pt` → `STEP_TRAIL_GAP = 8pt` |

### Changed
- `PluginVersion.FULL` / `NUMBER` bumped to `1.4.1`
- `pom.xml` version bumped to `1.4.1`

---

## [1.4.0] — 2026-04-07

### Fixed

- **SC1** — Screenshot / embedding compatibility across ALL Cucumber JVM versions (4.x–7.x).
  Both `mime_type` and `mimetype` field variants now resolved via `getMimeType()` helper.
- **SC2** — DocString `content` / `value` field fallback added for Cucumber 4/5/6 compatibility.
- `pom.xml` version `1.4.0`, README and CHANGELOG updated.

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
- **`FailureSummarySection`** — CI triage page inserted immediately after Dashboard.
- **`FeaturesSection` — Case ID column** — `tagPrefix` fully used.
- **`ScenariosSection` — Tags column** — each scenario row shows first two tags.
- **`ConsolidatedPdfGenerator` — 10-arg constructor** adding `displayFailureSummary`.
- **`ConsolidatedCompletionIT`** — 9 integration tests.

### Fixed
- Removed dead `pageReg` variable from `ConsolidatedPdfGenerator`
- Replaced wasteful `countPages(String)` helper with `doc.getNumberOfPages()`

---

## [1.2.0] — 2025-06-10

### Added
- **`reportMode` parameter** — `split`, `consolidated`, `both`
- **`DashboardSection`**, **`FeaturesSection`**, **`ScenariosSection`**,
  **`DetailedSection`**, **`TagStatsSection`**, **`ExpandedSection`**
- Two-pass page numbering
- `ConsolidatedPageCursor.PageNumberRegistry`

---

## [1.1.6] — 2025-04-xx

### Added
- F-07: Adaptive font size and two-line wrapping
- F-08: Step name word-wrap
- F-13: Multi-module JSON consolidation
- F-15: Parallel PDF generation
- F-16-config: Configurable tag prefix
- F-10: Runtime colour overrides
- F-06: Visual distinction between undefined/pending and skipped

---

## [1.0.0] — 2024-12-xx

### Added
- Initial release
