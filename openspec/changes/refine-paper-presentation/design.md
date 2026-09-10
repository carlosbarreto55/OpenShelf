## Context

`PaperCard` is the shared card used by search and library. It renders the title, a bookmark toggle, `formatMetadataLine(paper)` — a single `" · "`-joined string of compact authors, venue, year, type and citation count — the first topic as a pill, and `AccessActionRow`. `PaperDetailScreen` repeats the same joined-line approach with `formatDetailMetadataLine`, renders topics as pills, then `AccessActionRow`, then the abstract in full.

`AccessActionRow` maps through `AccessResolution.toAccessAction()` — the single fail-closed mapping point — and renders a `TextButton` for either action. `PaperCard` takes only a `Paper`, so no download state can reach it; `SearchViewModel.startDownload` therefore produces no visible change on the card that was tapped.

`Type.kt` sets every style to `FontFamily.SansSerif`.

Constraints: `toAccessAction()` remains the only place access is interpreted; no screen may branch on `AccessResolution` directly. Nothing here may make a download control appear for a paper that has no confirmed action. `MaterialTheme.spacing` remains the source of spacing values.

## Goals / Non-Goals

**Goals:**

- Card and detail metadata become scannable groups rather than one joined run.
- The confirmed action gets emphasis proportional to its importance, on both card and detail.
- A download's state is visible on the paper the user acted on.
- Paper text reads as scholarly content rather than as generic UI text.

**Non-Goals:**

- Changing which access states yield an action, or introducing any badge, lock, label or affordance for a state that yields none.
- Adding a provenance row. The team has ruled it non-mandatory; `AccessAction` keeps carrying `provenance` for a future decision, and nothing renders it.
- Changing the download lifecycle, workers, or `DownloadRecord`.
- Changing search chrome, loading, empty or error presentation.

## Decisions

### 1. Metadata grouping is a data shape, not a formatting string

Replace `formatMetadataLine` with a small presentation model — attribution + venue for line one, and an ordered list of fact strings plus an optional topic for line two — built by one function shared by card and detail. Each line renders only if it has content, which is what makes the "omit rather than render an empty row" scenario testable without reading pixels.

Chosen over keeping joined strings and inserting a line break: a joined string cannot express "this line is empty" or "the venue keeps full-strength colour while the facts are muted", and it is what produced the current problem.

### 2. Emphasis comes from the existing colour roles, not new ones

The confirmed PDF action uses `primary` / `onPrimary`; confirmed open full text uses `tertiaryContainer` / `onTertiaryContainer`. Both roles already exist in `Color.kt` for exactly this purpose — the amber tertiary container was defined as "the amber Read online action" and has never been used. No new colour values are introduced.

### 3. Download state renders inside `AccessActionRow`, behind the same fail-closed gate

`AccessActionRow` gains an optional `downloadState` parameter. It is consulted **only after** `toAccessAction()` has already yielded an action; if the mapping yields `null`, the composable returns without rendering anything, regardless of what download state was passed. That ordering is the invariant — a stale or malicious `DownloadRecord` can never introduce a control for an unverified paper. Assert it directly in a test that passes a running download alongside `AccessResolution.Unknown`.

Alternative rejected: a separate download row beneath the action. It adds height on every card and splits one conceptual control into two.

### 4. The record is joined at the presentation layer, not merged into `Paper`

`Paper` is a domain model shaped by the remote contract; a download is local state with its own lifecycle. ViewModels combine the papers flow with `DownloadsRepository`'s records into a `PaperListItem(paper, download)` for the UI. Merging a `DownloadRecord?` into `Paper` would put local mutable state inside the type the mapper produces and would ripple into Room mapping and every mapper test.

`PaperCard` therefore takes `PaperListItem`. Library and search both change at the call site; detail passes a single item.

Cost: three ViewModels gain a combine. Accepted — it keeps the domain boundary intact.

### 5. Detail keeps its action in a persistent bar

The abstract is the longest content on the screen and the action currently sits above it, so it scrolls away. Moving it to a bar pinned to the bottom of the detail scaffold keeps it reachable and gives save and cite (a later change) a stable home. The bar renders nothing at all when there is no confirmed action — it is not an empty chrome element.

### 6. Typography splits by role, with bundled fonts

`Type.kt` gains two families: a sans for chrome (labels, buttons, chips, navigation, metadata) and a serif for paper content (card titles, detail title, abstract). Both are bundled under `res/font/` rather than fetched — the app must render identically offline, and downloadable fonts add a failure mode on the first screen.

Only titles and abstracts change family. Metadata, chips and buttons stay sans, because they are UI, not prose.

Risks accepted: APK size, and the need to verify licences permit redistribution. Both are checked as tasks before the family is committed.

Alternative rejected: keeping `FontFamily.SansSerif` and differentiating by weight alone. It does not distinguish content from chrome, which is the actual goal.

### 7. Line limits change with the type

A serif at a larger optical size occupies more width per character than the current 16sp sans. Card title `maxLines` and the abstract clamp length are re-derived after the family lands, not carried over — otherwise the first visible symptom of the type change is truncated titles.

## Risks / Trade-offs

- [Download state leaks a control onto an unverified paper] → `toAccessAction()` is consulted first and short-circuits; covered by an explicit test pairing a running record with each non-actionable state.
- [Three ViewModels combining flows introduces recomposition churn] → Combine at the ViewModel, emit an immutable list of stable items keyed by paper ID, and keep `PaperListItem` `@Immutable`; verify with a recomposition count on a scrolling list.
- [Two font families increase APK size] → Subset to Latin and the weights actually used; measure the delta and record it before and after.
- [Font licences do not permit redistribution] → Verified as a task before the family is committed; falls back to `FontFamily.SansSerif` with no other change if it fails.
- [Serif titles truncate where sans titles did not] → Re-derive `maxLines` and the abstract clamp after the family lands, and check at the largest supported font scale.
- [The persistent detail bar competes with the navigation bar] → Detail is a pushed destination with no bottom navigation, so the bar has the space; verify with gesture-navigation insets.
- [Grouped lines regress screen-reader flow] → Group the two metadata lines under one semantics node with a single readable description, so a card is announced as one coherent item rather than five fragments.

## Migration Plan

1. Introduce the grouped metadata model and switch card and detail to it, keeping the current type and the current `TextButton`.
2. Raise action emphasis on card and detail; move the detail action into the persistent bar.
3. Introduce `PaperListItem`, thread download state to the surfaces, render the states in the action slot.
4. Add the font families and split `Type.kt`; re-derive line limits.

Each step is independently revertible. Step 4 is the only one with an external dependency (font licences) and is deliberately last so the rest can ship without it.

## Open Questions

- Which two families? The review mockups used Newsreader with IBM Plex Sans; the split between chrome and content is the decision, the specific pairing is not settled.
- Should a completed download replace the Download action with Open on the card, or add Open beside it? Replacing is simpler and matches the Downloads screen; confirm before implementing step 3.
