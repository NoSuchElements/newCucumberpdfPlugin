# Changelog — Cucumber PDF Reporter

All notable changes documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [1.5.0] — 2025-07-xx  Production-grade release

### Added

- **`ReportMetadata`** — new model class that binds to a `<metadata>` Maven
  configuration block. Supports `environment`, `branch`, `build`, `appVersion`,
  `browser`, `os`, `baseUrl` fields, plus arbitrary `put(key, value)` entries.
  Rendered as a two-column key-value table at the bottom of the Dashboard page.

- **`SlowTestsSection`** — new consolidated-mode section listing the top-N
  slowest scenarios sorted by duration descending. Duration cells are
  colour-coded: red for >10 s, amber for >5 s. Controlled by:
  - `displaySlowTests=true` (default: `false`)
  - `slowTestTopN` (default: `15`, max: `50`)

- **`MetadataSection`** — shared inline renderer for the metadata block used
  by `DashboardSection`. Stateless and reusable.

- **PDF document properties** — both `FeaturePdfGenerator` and
  `ConsolidatedPdfGenerator` now set `title`, `subject`, and `creator` fields
  in `PDDocumentInformation`. The consolidated report also writes the
  `Environment` custom metadata field when configured.

- **Split mode: DataTable rendering** — `FeaturePdfGenerator` now renders
  Cucumber DataTable rows with a dark header row and monospace text, matching
  the consolidated `DetailedSection` layout.

- **Split mode: DocString rendering** — `FeaturePdfGenerator` now renders
  DocString blocks with an indigo left-accent stripe, capped at 25 lines.

- **Split mode: output log lines** — `FeaturePdfGenerator` now renders
  step `output` lines in a grey monospace block below the step.

- **Split mode: Background steps** — `FeaturePdfGenerator` now renders
  Background steps in a labelled block with a horizontal divider, before the
  scenario's own steps.

- **Split mode: scenario tags** — tags from `CucumberScenario.getTags()` are
  now rendered as an italic line below the scenario header band.

- **Step keyword `*`** — the asterisk wildcard keyword is now treated as a
  primary keyword (not `And`/`But` continuation) in both split and consolidated
  renderers.

### Fixed

- **Bug 1 (split mode crash on exception features)** — `FeaturePdfGenerator`
  previously held one `PDPageContentStream` open for an entire scenario while
  a stub `addContinuationPage` only clamped `y`. Long Java stack traces
  (CRLF-delimited, tab-prefixed) drove `y` below the page bottom; PDFBox threw
  `IllegalStateException`. Fixed by introducing `ScenarioCursor`, which opens
  a fresh APPEND-mode stream per drawing call and calls `doc.addPage()` on
  overflow.

- **Bug 2 (images not attached)** — `step.getEmbeddings()` was populated by
  the parser but never rendered in split mode. Both step-level and hook-level
  embeddings are now rendered inline with a white card frame.

- **`CucumberScenario.getAllScreenshots()`** — before-hook embeddings were
  omitted. Added `beforeHooks` as the first collection pass.

- **Step spacing/padding (1.4.1 branch fix)** — `LH` raised from 14 f to 15 f
  for better breathing room. Extra 2 f inter-step gap added after each step
  block. Post-header padding increased from 0 to 8 f. Continuation-keyword
  indentation tightened for visual hierarchy.

- **Version number inconsistency** — `SplitPdfReporterMojo.banner()` reported
  `v1.1.6` while section footers reported `v1.2.0`. All version references
  now use the central `ConsolidatedPdfGenerator.VERSION` constant (`"1.5.0"`).

- **Adaptive feature/scenario name font sizing** — header band now
  auto-shrinks the font when names exceed 40 / 55 characters, preventing
  truncation of long feature or scenario names (CHANGELOG F-07 item, now
  implemented).

---

## [1.2.0] Completion additions — 2025-06-10

### Added
- `FailureSummarySection` — CI triage page after Dashboard
- `FeaturesSection` Case ID column
- `ScenariosSection` Tags column
- `ConsolidatedPdfGenerator` 10-arg constructor (added `displayFailureSummary`)
- `ConsolidatedCompletionIT` — 12 integration tests

### Fixed
- Dead `pageReg` variable removed from `ConsolidatedPdfGenerator`
- Replaced re-open-file page counter with `doc.getNumberOfPages()`

---

## [1.2.0] — 2025-06-10

### Added
- `reportMode`: `split` | `consolidated` | `both`
- `DashboardSection`, `FeaturesSection`, `ScenariosSection`, `DetailedSection`,
  `TagStatsSection`, `ExpandedSection`, `ContentBlockRenderer`
- Two-pass page numbering via `stampPageNumbers`
- New Mojo parameters: `consolidatedReportName`, `reportTitle`,
  `displayDashboard/Feature/Scenario/Detailed/Expanded/TagStats`
- `ConsolidatedPdfGeneratorIT` — 6 integration tests

---

## [1.1.6] — 2025-04-xx

### Added
- F-07: Adaptive font / two-line wrapping in headers (partial)
- F-08: Step name word-wrap across two lines in DetailedPage
- F-13: Multi-module JSON consolidation
- F-15: Parallel PDF generation
- F-16: Configurable tag prefix
- F-10: Runtime colour overrides

---

## [1.1.3] — 2025-03-xx

### Added
- F-01: And/But keyword visual hierarchy
- F-02: Scenario tags in detail page header
- F-03: Scenario Outline row context bar
- F-04: Configurable output log line limit
- F-05: Continuation page context header

---

## [1.0.0] — 2024-12-xx

- Initial release: one PDF per Cucumber feature from JSON results
