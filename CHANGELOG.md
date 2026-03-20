# Changelog — Cucumber PDF Reporter

All notable changes are documented here.  
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

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
  (was created but never passed to section cursors — page numbering works correctly
  via the second-pass `stampPageNumbers` loop which needs no registry)
- Removed unused `PageNumberRegistry` import from `ConsolidatedPdfGenerator`
- Replaced wasteful `countPages(String)` helper (re-opened saved file) with
  `doc.getNumberOfPages()` on the still-open document

### Changed
- `ScenariosSection` column layout adjusted to fit Tags column:
  Name truncated to 30 chars, Tags max 20 chars (2 tags + overflow count)
- `FeaturesSection` column layout adjusted: Name truncated to 30 chars,
  new Case ID column at 40% of content width

---

## [1.2.0] — 2025-06-10

### Added
- **`reportMode` parameter** — controls which PDF(s) are generated:
  - `split` — one PDF per feature (original v1.x behaviour, default)
  - `consolidated` — single PDF with all sections (grasshopper7 layout)
  - `both` — generates both simultaneously in one execution
- **`DashboardSection`** — overall metric cards (features / scenarios / steps / duration),
  segmented distribution bars with legend, and a features-at-a-glance table
- **`FeaturesSection`** — full feature table with URI, status, counts, and progress bars
- **`ScenariosSection`** — all scenarios grouped by feature with mini progress bars
- **`DetailedSection`** — step-by-step breakdown: keyword hierarchy (F-01 And/But),
  error blocks, step output logs, data tables, and DocStrings
- **`TagStatsSection`** — per-tag scenario pass/fail/skip table sorted by failure
  count; includes inline progress bars and pass-rate percentages
- **`ExpandedSection`** — screenshots grouped by scenario (opt-in via `displayExpanded=true`)
- **`ContentBlockRenderer`** — shared drawing primitive used by both Detailed and
  Expanded sections; eliminates all rendering duplication
- **Two-pass page numbering** — `ConsolidatedPdfGenerator.stampPageNumbers()` stamps
  "Page N of T" in the footer of every page after the document is fully built
- **`ConsolidatedPageCursor.PageNumberRegistry`** — thread-safe page-index collector
  for the second-pass stamper
- **New Mojo parameters:**
  - `consolidatedReportName` — output filename (default: `cucumber-report.pdf`)
  - `reportTitle` — Dashboard page title
  - `displayDashboard`, `displayFeature`, `displayScenario`, `displayDetailed`,
    `displayExpanded`, `displayTagStats` — section visibility flags
- **`ConsolidatedPdfGeneratorIT`** — 6 integration tests covering full report,
  dashboard-only, screenshots, `ReportStats` accuracy, custom title, and
  tag-stats collection

### Changed
- `SplitPdfReporterMojo` extended with all consolidated parameters; all existing
  split-mode parameters remain unchanged — no breaking changes to v1.x configurations
- `pom.xml` version bumped to `1.2.0`; description updated to cover both modes
- `README.md` fully rewritten with parameter reference table, layout diagrams,
  and configuration examples for all three modes

### Architecture
- New package `com.nosuchelements.consolidated` with 5 supporting classes
- New package `com.nosuchelements.consolidated.sections` with 6 section classes
- Zero changes to existing model classes (`CucumberFeature`, `CucumberScenario`,
  `CucumberStep`, etc.) — purely additive

---

## [1.1.6] — 2025-04-xx

### Added
- F-07: Adaptive feature/scenario name font size and two-line wrapping in headers
- F-08: Step name word-wrap across two lines in `DetailedPage`
- F-13: Multi-module JSON consolidation (`consolidate`, `scanRoot`, `cucumberJsonPattern`)
- F-15: Parallel PDF generation (`parallel=true`)
- F-16-config: Configurable tag prefix (`tagPrefix`) — supports JIRA, RALLY, custom prefixes
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
- F-05: Continuation page context header ("[continued] ScenarioName")

---

## [1.1.1] — 2025-02-xx

### Changed
- Removed SummaryPage from default page set (parameter `includeSummaryPage` retained but ignored)
- FeaturePage redesigned: modern slate/indigo colour palette, metric cards, progress bar, scenario list

---

## [1.0.0] — 2024-12-xx

### Added
- Initial release
- One PDF per Cucumber feature from JSON results
- FeaturePage + DetailedPage structure
- Apache PDFBox 2.0.30 baseline
- Basic colour scheme (green/red/amber)
