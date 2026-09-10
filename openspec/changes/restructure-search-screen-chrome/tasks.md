## 1. Extract the control region (no visual change)

- [ ] 1.1 Move the header, search field, and filter/sort row out of the `LazyColumn` in `SearchScreen.kt` into a `SearchControls` composable rendered above the list inside a `Column`, keeping current spacing and appearance identical.
- [ ] 1.2 Verify the existing prefetch `LaunchedEffect` still reads `listState.layoutInfo` correctly now that the list no longer contains the three control items, and adjust `PREFETCH_THRESHOLD` accounting if the index arithmetic shifted.
- [ ] 1.3 Run existing search unit and Compose tests; fix any test that asserted control items by list index rather than by semantics.

## 2. Collapse behaviour

- [ ] 2.1 Add an `expanded: Boolean` parameter to `SearchControls` with a collapsed layout: reduced-height query field carrying the current query, the same chip row, and the same sort control.
- [ ] 2.2 Derive `expanded` from `LazyListState` scroll position with hysteresis (collapse threshold above expand threshold) as local Compose state, not `SearchUiState`.
- [ ] 2.3 Ensure both states emit the same semantics tree — same labels, roles, selected states, and traversal order for query, each filter chip, and sort.
- [ ] 2.4 Add a Compose test that scrolls past the first page and asserts the query text, each filter chip, and the sort control are still present and operable.
- [ ] 2.5 Add a Compose test that changes a filter from the collapsed controls and asserts the list returns to its first item.

## 3. Pre-result density

- [ ] 3.1 Reduce the hero to a single supporting line and apply the reference's tighter inter-row spacing, adding tokens to `PaperReaderSpacing` where a new value is needed rather than literal `dp` at call sites.
- [ ] 3.2 Confirm every interactive element in the control region remains at or above the 48dp touch-target floor in both expanded and collapsed states.
- [ ] 3.3 Verify with a layout inspection that the first result card is fully visible in the first content viewport at default font scale on a supported phone width; record the measured offset in the change notes.
- [ ] 3.4 Verify reflow without clipping at the largest supported font scale, in both light and dark themes.

## 4. Demo disclosure relocation

- [ ] 4.1 Add a `DemoDisclosurePreferences` interface (read + acknowledge) and a DataStore-backed implementation, bound in the DI graph, with an in-memory fake for tests.
- [ ] 4.2 Remove `DemoDataNotice` from `PaperReaderApp` so no destination reserves layout for it, leaving the `NavHost` as the sole content of the scaffold body.
- [ ] 4.3 Add `DemoDisclosureState` (`Hidden`, `FirstRun`, `IndicatorOnly`) to `SearchUiState`, derived from `BackendConfiguration.mode` and the stored acknowledgement.
- [ ] 4.4 Render the dismissible first-run explanation on the search screen and wire dismissal to the acknowledgement.
- [ ] 4.5 Add the compact, non-dismissible demo indicator to the search header; activating it reopens the full explanation.
- [ ] 4.6 Confirm no demo explanation or indicator renders when the configured mode is not fake.
- [ ] 4.7 Confirm the in-download demo disclaimer (`downloads_demo_placeholder_notice`) is unaffected by dismissal.
- [ ] 4.8 Add tests: first launch shows the explanation; dismissal persists across ViewModel recreation; indicator remains after dismissal; nothing renders in non-fake mode.

## 5. Zero-query content

- [ ] 5.1 Define the suggested-topic constant list in the presentation layer with no counts, ranking, or authority claims, and expose it as `suggestedTopics` on `SearchUiState`.
- [ ] 5.2 Replace `InitialContent`'s placeholder `Surface` with the suggestions, keeping the existing recent-queries row.
- [ ] 5.3 Route suggestion activation through `SearchQuery.normalize` and the same search path as typed input so it debounces, caches, and records into recent queries identically.
- [ ] 5.4 Remove the now-unused `search_shell_title` / `search_shell_description` strings and add strings for suggestions and the demo disclosure.
- [ ] 5.5 Add tests: suggestions render with no recent queries; activating a suggestion issues a search and appears in recent queries afterwards.

## 6. Validation

- [ ] 6.1 Run `:app:testDebugUnitTest` and `:app:connectedDebugAndroidTest`.
- [ ] 6.2 Run `:app:lint` and `:app:assembleDebug`.
- [ ] 6.3 Manually verify: scroll-and-refine, collapse threshold has no jitter, first-run explanation and dismissal, indicator reopen, suggestions, rotation, and process restart.
- [ ] 6.4 Confirm no change to access rendering, filter set, remote contracts, or Room schema was introduced by this change.
