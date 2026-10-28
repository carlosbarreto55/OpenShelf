## Context

`RepositoryError` is a sealed taxonomy — `Offline`, `Timeout`, `Http`, `Contract`, `AccessValidation`, `Unexpected` — each carrying a safe `userMessage` and an `isRecoverable` flag. `SearchContent.Error` reduces it to a `message: String`, and `RecoverableErrorState` renders that under a hardcoded "Something went wrong" title with an unconditional "Try again". The cause is thrown away one layer above the UI that needed it.

`LoadingState` is a centred `CircularProgressIndicator` emitted as a single `LazyColumn` item, so it neither centres in the viewport nor preserves the list's geometry.

`EmptyState` takes one optional action, wired to `onClearFilters`.

The result count uses `pluralStringResource(R.plurals.search_result_count, content.papers.size, …)`. `PaperSearchResponseDto` has no total field and `CursorPage<T>` carries only `items` and `nextCursor`.

`DownloadsScreen` renders `content.records` as one flat `items` list.

Constraints: no new information about a paper's access may appear in any of these states. Error text must remain the repository's safe message — never a raw payload. `RemoteJson` already sets `ignoreUnknownKeys = true` and `explicitNulls = false`.

## Goals / Non-Goals

**Goals:**

- The UI consumes the error taxonomy that already exists instead of flattening it.
- Loading holds layout rather than replacing it.
- The empty state is actionable per filter.
- Counts never assert more than the data supports.
- Downloads are grouped by required action.

**Non-Goals:**

- Adding retry policy, backoff, or connectivity monitoring. This change presents failures; it does not change how they are produced.
- Adding filters. The empty state describes whichever filters are active.
- Changing card content, access presentation, or search chrome.
- Speculative queries to discover what an unfiltered search would return.

## Decisions

### 1. The error cause reaches the UI as a domain value, not a string

`SearchContent.Error` carries the `RepositoryError` itself rather than a pre-flattened message. The UI maps cause → (headline, explanation, primary action, secondary route) in one place, so the mapping is a single testable function and adding a future variant is a compile error rather than a silent fallthrough — the `when` is exhaustive over the sealed taxonomy.

Alternative rejected: adding a `title` alongside `userMessage` on `RepositoryError`. That pushes presentation copy into the domain layer, which is where `userMessage` is already an uncomfortable fit; it should not gain company.

### 2. Every error offers a route that can actually succeed

`isRecoverable` governs whether a retry is offered at all. Independently, each cause names a second route that works under that failure: offline routes to the Library, which reads from Room. A screen whose only action is one that cannot work under the stated condition is the defect being fixed, so "offer a retry anyway" is not an acceptable fallback.

### 3. Skeletons are a shape, not an animation requirement

`LoadingState` is joined by a skeleton list that renders placeholder blocks in the proportions of a real card. Whether they shimmer is a visual detail; holding the geometry is the requirement, and it is what makes the arrival of results reflow-free. The skeleton count is fixed and small — it must not imply how many results are coming.

The existing `LoadingState` is kept for cases with no list geometry to preserve (detail, library first load), rather than being deleted.

### 4. The empty state is driven by an enumerated list of active filters

The UI needs "which filters are on, each with a label and a removal action". Deriving that by comparing `PaperFilters` to `PaperFilters()` field by field at the call site would have to be rewritten every time a filter is added — including when the deferred year/type/topic filters land. Instead, one function maps `PaperFilters` to a list of `ActiveFilter(label, clear: () -> PaperFilters)`; the empty state and the chip row both read it, and a new filter is added in exactly one place.

Not doing: showing what the unfiltered result count would be. That needs a second speculative request per empty result, and the count would be a claim the client cannot support offline. Left as an open question.

### 5. The total is optional at every layer, and never synthesised

`PaperSearchResponseDto` gains a nullable total; `CursorPage<T>` gains `totalItems: Int?`; the cached query row gains a nullable column. Absence propagates as `null` all the way to the copy, which then states only what is shown.

The critical rule is negative: nothing anywhere substitutes `items.size`, the page size, or a running sum when the total is absent. That is the failure mode being fixed, and it is asserted directly in tests.

Copy: "Showing N papers" without a total; "Showing N of M papers" with one.

Room migration: one nullable column on the cached-query table, defaulting to null — additive, so the migration is a single `ALTER TABLE` and existing cached rows report no total, which is correct.

### 6. Downloads group by required action, not by state name

Three groups: in progress (queued, running), needs attention (failed), on this device (completed). Canceled records are not a group — a canceled download is a decision the user already made, so it is removed from the list rather than kept as a category.

Grouping is computed in `DownloadsViewModel` and emitted as ordered sections, so ordering is deterministic and testable rather than dependent on DAO return order.

"Clear completed" deletes the records and their managed local files together — a record without its file, or a file without its record, is the leak this control exists to prevent.

### 7. Failure text names the cause without inventing one

The download failure copy comes from the record's existing `errorMessage` plus a cause-specific heading, following the same rule as search errors: safe text only. The PDF-validation failure in particular must say that no partial file was kept, because that is the fact the user needs and it is already what the implementation does.

## Risks / Trade-offs

- [Cause-specific copy multiplies strings to maintain] → Six causes, one mapping function, one string per cause; the alternative is the generic message that made the taxonomy pointless.
- [Skeletons imply how many results are coming] → Fixed small count, no numeric affordance, and the count line renders only once real results arrive.
- [The optional total is synthesised somewhere by accident] → Assert it: a test decodes a fixture with no total, pages twice, and fails if any total is displayed.
- [Room migration on a cached-query table] → Additive nullable column with a migration test; cache is expirable, so a failed migration can fall back to dropping cached search rows without touching saved papers or downloads.
- [Clear-completed deletes a file another surface still references] → Deletion goes through the downloads repository, which owns both record and file; saved-paper state is independent and untouched, as the offline-library capability already requires.
- [Grouping hides a record the user was looking for] → Groups are always in the same order and never collapsed by default; an empty group renders nothing rather than an empty heading.

## Migration Plan

1. Carry `RepositoryError` through to `SearchContent.Error` and add the cause → presentation mapping. No visual change beyond the copy.
2. Add skeletons for the search list.
3. Add the active-filter model and the filter-aware empty state.
4. Add the optional total through DTO, domain, Room (migration), and copy.
5. Group the Downloads screen and add clear-completed.

Steps 1–3 and 5 are independently revertible. Step 4 carries the schema change and should land on its own commit so it can be reverted without taking the rest with it.

## Open Questions

- Should the empty state show what an unfiltered search would return? It is the most useful version and the most expensive; deferred until there is a backend that can answer it in the same response.
- Should `Timeout` offer cached content from a *different* query, or only a retry? Currently only a retry; revisit once the library has enough content for the alternative to be worth offering.
