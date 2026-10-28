## Why

`RepositoryError` carefully separates `Offline`, `Timeout`, `Http`, `Contract`, `AccessValidation` and `Unexpected`, each with its own `userMessage` and `isRecoverable` — and then `RecoverableErrorState` renders every one of them under a hardcoded "Something went wrong" with a single "Try again" button, including the offline case, where trying again is precisely the thing that cannot work. Loading replaces the entire result list with a centred spinner, so each settled search costs two layout jumps. The empty state offers only an all-or-nothing "Clear filters" with no indication of which filters are narrowing the search. The result count renders `content.papers.size` — the number *loaded*, not the number *matched* — so it silently grows from "3 results" to "6 results" as the user pages, contradicting itself. And the Downloads screen is one flat list in whatever order Room returns, mixing a running transfer, a failure that needs a decision, and files the user already has.

None of this is a data problem. Every distinction the UI needs is already modelled; the presentation layer discards it.

## What Changes

- **Skeleton loading.** A first search renders card-shaped placeholders that hold the list geometry instead of a centred spinner that replaces it.
- **Errors named by cause.** Each `RepositoryError` variant gets its own headline, its own explanation, and a second route that still works — notably, offline offers the Library rather than a retry that cannot succeed.
- **Filter-aware empty state.** When a search returns nothing and filters are active, each active filter is listed and individually removable alongside a clear-all.
- **Truthful result counts.** The count states how many results are currently shown, and states the matched total when the response supplies one. The remote contract gains an optional total so the backend can supply it later without a client change.
- **Grouped downloads.** The Downloads screen groups records into in-progress, needing attention, and completed, with failure text that names the cause and a control to clear completed records.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `paper-discovery`: `Discovery screen states` gains requirements for skeleton loading, cause-specific error presentation with a working alternative route, and a filter-aware empty state; `Topic search` gains a definition of what the displayed count means.
- `paper-downloads`: adds a requirement for how the download list is organised and how failures are explained.
- `backend-ready-data-layer`: adds an optional matched-result total to the search response contract, absent-safe by construction.

### Explicitly unchanged

- `verified-paper-access` is untouched. No state introduced here reveals anything about a paper's access.
- Which filters exist is unchanged; the empty state describes whichever filters are active.

> Sequencing: these capabilities are still defined by the unarchived `build-openalex-paper-reader` change, so these deltas assume that change lands (or is archived into `openspec/specs/`) first. This change is independent of `restructure-search-screen-chrome` and `refine-paper-presentation` except that all three touch `SearchScreen.kt`.

## Impact

- **Android code:** `ui/components/StateComponents.kt` (skeletons, cause-specific error surface, filter-aware empty surface), `ui/search/SearchScreen.kt` and `SearchUiState` (error cause reaches the UI, count semantics), `ui/downloads/DownloadsScreen.kt` and `DownloadsViewModel.kt` (grouping, clear completed), `res/values/strings.xml` and `strings` plurals.
- **Remote contract:** `PaperSearchResponseDto` gains an optional total; `RemoteJson` already sets `ignoreUnknownKeys`, so absence is safe and no fixture must be rewritten to add it.
- **Domain:** `CursorPage<T>` gains an optional total. `RepositoryError` is unchanged — the UI stops discarding it.
- **Room:** cached search queries gain an optional stored total so a cached result set can still report one; this is a schema version bump and a migration.
- **Tests:** state-component tests per error cause; search tests for count semantics across pagination; downloads tests for grouping and clear-completed.
- **Not affected:** access resolution, mappers' fail-closed rules, download workers, PDF validation.
