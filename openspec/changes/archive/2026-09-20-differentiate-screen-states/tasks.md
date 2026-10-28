## 1. Errors named by cause

- [x] 1.1 Change `SearchContent.Error` to carry the `RepositoryError` rather than a flattened message, and update `SearchViewModel` accordingly.
- [x] 1.2 Add a single mapping function from `RepositoryError` to headline, explanation, primary action and secondary route, exhaustive over the sealed taxonomy.
- [x] 1.3 Replace the hardcoded "Something went wrong" in `RecoverableErrorState` with the mapped presentation; keep the composable's live-region semantics.
- [x] 1.4 Offer a retry only when `isRecoverable` is true.
- [x] 1.5 Give the offline cause a route to the Library, and give every other cause a second route that works under that failure.
- [x] 1.6 Confirm only the repository's safe `userMessage` reaches the UI — no payloads, URLs, stack traces or credentials.
- [x] 1.7 Apply the same error surface on paper detail and library.
- [x] 1.8 Add tests: one per cause asserting headline, retry presence, and the secondary route.

## 2. Skeleton loading

- [x] 2.1 Add a skeleton list component rendering a fixed small number of card-shaped placeholders in the proportions of a real `PaperCard`.
- [x] 2.2 Use it for the search loading state; keep the existing `LoadingState` for surfaces with no list geometry to preserve.
- [x] 2.3 Confirm no layout reflow occurs when real results replace the skeletons.
- [x] 2.4 Confirm the skeleton exposes a single polite live-region announcement and does not announce per placeholder.
- [x] 2.5 Add a test asserting skeletons render while loading and are replaced without changing the list's content padding or arrangement.

## 3. Filter-aware empty state

- [x] 3.1 Add a function mapping `PaperFilters` to a list of active filters, each with a label and a function returning the filters with that one cleared.
- [x] 3.2 Read that list from both the chip row and the empty state so a future filter is added in one place.
- [x] 3.3 Render the empty state with each active filter individually removable plus a clear-all control.
- [x] 3.4 Render the empty state without filter language when no filters are active.
- [x] 3.5 Add tests: empty with two active filters removes one and re-searches; empty with no filters shows no filter language.

## 4. Truthful result counts

- [x] 4.1 Add an optional matched-result total to `PaperSearchResponseDto` with its `@SerialName` wire name.
- [x] 4.2 Add `totalItems: Int?` to `CursorPage<T>` and carry it through the mapper.
- [x] 4.3 Add a nullable total column to the cached-query Room entity, bump the schema version, and write the additive migration plus a migration test.
- [x] 4.4 Carry the stored total back out when cached results are displayed.
- [x] 4.5 Replace the `search_result_count` plural with copy that states how many are shown, and a second form that states shown-of-total when a total exists.
- [x] 4.6 Add a test that decodes a fixture with no total, loads two pages, and fails if any total is displayed or if `items.size` is presented as a total.
- [x] 4.7 Confirm existing fixtures decode unchanged with the new optional field.

## 5. Downloads grouping

- [x] 5.1 Compute ordered sections in `DownloadsViewModel` — in progress (queued, running), needs attention (failed), on this device (completed) — rather than relying on DAO order.
- [x] 5.2 Remove canceled records from the list rather than giving them a group.
- [x] 5.3 Render only groups that have records; render no heading for an empty group.
- [x] 5.4 Give failed records a cause-specific heading plus the record's existing safe `errorMessage`, and state that no partial file was kept.
- [x] 5.5 Add "clear completed", deleting completed records and their managed local files together through the downloads repository.
- [x] 5.6 Confirm clear-completed leaves in-progress records, failed records, and saved-paper state intact.
- [x] 5.7 Add tests: mixed-state grouping and order; single-state list renders one group; clear-completed removes records and files and leaves the rest.

## 6. Validation

- [x] 6.1 Confirm no state introduced here reveals anything about a paper's access state.
- [x] 6.2 Run `:app:testDebugUnitTest`, `:app:connectedDebugAndroidTest`, `:app:lint`, and `:app:assembleDebug`.
- [x] 6.3 Run the Room schema check and confirm the exported schema matches the bumped version.
- [x] 6.4 Manually verify each error cause with airplane mode and a forced failure, loading, empty with and without filters, counts across two pages, and downloads in mixed states.
