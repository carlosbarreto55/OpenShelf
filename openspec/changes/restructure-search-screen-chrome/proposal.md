## Why

The search screen puts every control — hero, query field, filter chips, sort — inside the same scrolling list as the results, so once a user pages past the first few papers there is no way to refine the query, drop a filter or re-sort without scrolling back to the top. Cursor pagination is already implemented, so the list is designed to get long. At the same time the permanent demo banner, the hero and the control stack consume roughly 55% of the first content viewport before a single paper appears, and the zero-query state is a placeholder card ("Your research space is ready.") that neither explains anything nor does anything.

## What Changes

- Split the search screen into a **persistent control region** and a scrolling result region. The query field, active filters and sort control remain reachable at every scroll position; the hero collapses out of the way once results are showing.
- Reduce the vertical cost of the pre-result region so the first result card is visible within the first content viewport on a supported phone width.
- **Relocate the demo-mode disclosure.** Replace the permanently pinned, non-dismissible notice above the `NavHost` with (a) a dismissible first-run explanation and (b) a compact, always-present demo indicator in the search header that opens the same explanation. The full-strength disclaimer moves to the point where it is actually load-bearing — inside a simulated download.
- **Replace the placeholder zero-query content** with usable first-run material: suggested topics that submit a real search, plus the existing recent queries.
- No change to the fail-closed access rules, to which filters exist, or to any remote contract.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `paper-discovery`: adds requirements for persistent search controls across scroll positions, first-result visibility within the initial viewport, and substantive zero-query content. No existing discovery requirement changes meaning.
- `backend-ready-data-layer`: adds a requirement governing *where* placeholder mode is disclosed and how that disclosure is dismissed. Today this is only a risk note in the baseline `design.md`, never a requirement.

> Sequencing: both capabilities are still defined by the unarchived `build-openalex-paper-reader` change, so these deltas assume that change lands (or is archived into `openspec/specs/`) first.

## Impact

- **Android code:** `ui/search/SearchScreen.kt` (control region extracted from the `LazyColumn`, scroll-driven collapse, zero-query content), `ui/search/SearchViewModel.kt` and `SearchUiState` (suggested topics, first-run acknowledgement), `ui/navigation/PaperReaderApp.kt` (removal of the pinned notice from the app shell), `ui/components/StateComponents.kt` (`DemoDataNotice` becomes a dismissible first-run surface plus a compact indicator), `res/values/strings.xml`.
- **Local data:** one persisted boolean for "first-run demo explanation dismissed". No Room schema change if stored in preferences; if stored in Room it is a new single-row table and a schema version bump.
- **Tests:** search Compose semantics tests for control reachability after scrolling, first-run content, and demo-indicator presence; existing search ViewModel tests unaffected.
- **Not affected:** remote DTOs, mappers, repositories, access resolution, download lifecycle.
