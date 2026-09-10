## Context

`LibraryScreen` renders `LibraryContent.Loaded.papers` as one `LazyColumn` with no controls. `LibraryRepository` exposes an observable saved-papers flow and a save toggle. Saved state lives in Room alongside cached paper metadata, and `offline-paper-library` already requires saved papers to survive cache pruning.

Papers are shared rows: the same paper can appear in several cached search results and in the library without duplication, keyed by its stable `PaperId`. Collections must not break that — they are a second association over the same rows, exactly as the saved association is.

Constraints: account-free, offline-capable, no contract change, no new information about access. Cache maintenance must never remove a paper a collection references.

## Goals / Non-Goals

**Goals:**

- The library stays usable at a few hundred saved papers.
- Collections, membership and saved state are durable local data with clear ownership rules.
- Filtering and sorting are observable Room queries, not in-memory list manipulation.

**Non-Goals:**

- Sync, sharing, export or import of collections. (Citation export is a separate proposal.)
- Nested collections or tags with hierarchy. One flat level.
- Collection-aware behaviour anywhere outside the Library.
- Automatic or suggested collections.

## Decisions

### 1. Many-to-many join, not a column on the paper

A `collection` table and a `collection_paper` join table keyed by `(collectionId, paperId)`. A paper belongs to zero or more collections.

Chosen over a single `collectionId` column on the saved-paper row: a paper genuinely belongs in more than one place (a methods paper cited in two chapters), and the one-column model forces a destructive choice on the user the first time that happens. The join costs one table and buys the case that actually occurs.

Foreign keys cascade from both sides: deleting a collection deletes its memberships; removing a paper row deletes its memberships. Cascades are declared in Room rather than implemented in the repository, so no code path can leave an orphan.

### 2. Membership is a distinct association from saved state, with an explicit rule

Removing from a collection leaves the paper saved. Unsaving removes every membership. That asymmetry is deliberate and is the only sensible reading — a paper that is not in the library cannot be filed in it — but it is exactly the kind of rule that gets implemented inconsistently in two places, so it lives in one repository operation and is asserted in tests from both directions.

Deleting a collection never deletes papers. This is stated in the spec because it is the destructive-action question users will worry about, and the answer must be visible in the confirmation copy.

### 3. Filtering and sorting are SQL, not Kotlin

The DAO takes the text filter, the facet and the sort order and returns an observable, already-ordered flow. Filtering a list in the ViewModel would work at fifty papers and stop working at a few thousand, would re-sort on every emission, and would put ordering rules in two places once paging is ever added to the library.

Text matching runs over title, author names and venue. Author names are a related table, so the query joins them; `LIKE`-based matching with a normalised (case- and diacritic-folded) column is the starting point. FTS is deliberately not adopted yet — it is a second schema to migrate for a list that is small by definition, and the query can move to FTS later without changing the repository contract.

### 4. One library query object, not four independent controls

`LibraryQuery(text, facet, sortOrder)` is a single value the ViewModel holds and the DAO consumes. Every control edits one field of it. This keeps "filters and ordering combine" true by construction rather than by remembering to pass all three at every call site, and makes the combined-state tests a matrix over one type.

`facet` is a sealed type — `All`, `InCollection(id)`, `Unfiled`, `Downloaded` — so adding a facet is a compile error at the DAO rather than a silently unhandled branch.

### 5. The downloaded facet reads download records, never access state

`Downloaded` filters on the existence of a completed `DownloadRecord` for the paper. It must not consult `AccessResolution`, and it must not cause any access affordance to appear. The distinction matters: "I have this file" is a fact about this device; "this paper has free full text" is a claim about the world that only the backend may make. Assert it with a test where a paper has a completed download and a non-actionable access state, and check that the paper is listed and no access affordance renders.

### 6. Empty-with-filters is distinct from empty library

An empty library and a filter that matches nothing are different situations with different remedies. The filtered-empty state names the active filters and clears them individually — the same pattern the search change introduces, and it should reuse that component rather than growing a second one.

### 7. Cache maintenance gains a second protected reference

Pruning must retain any paper referenced by a saved association **or** a collection membership. Since unsaving already clears memberships, a membership can only exist on a saved paper today — but the retention query should test membership explicitly rather than relying on that invariant holding forever.

## Risks / Trade-offs

- [Migration adds two tables to a schema that already holds user data] → Purely additive; no existing table changes. Migration test asserts saved papers and download records survive it intact.
- [Text filter is slow on a large library] → `LIKE` on a normalised column with an index; the repository contract does not expose the matching strategy, so moving to FTS later needs no call-site change. Benchmark at a few thousand saved papers before closing the change.
- [Cascade deletes remove more than the user expected] → Cascades only ever remove memberships, never papers or downloads; the delete-collection confirmation states that papers stay saved.
- [Diacritic and case folding differs from what users expect] → Fold on write into a normalised column rather than folding in the query, so matching behaviour is one documented transformation.
- [The downloaded facet is read as an access signal] → Copy says "downloaded", never "available" or "open"; covered by the fail-closed test above.
- [Collections leak into other screens] → Scope is the Library. A paper's detail screen may offer assignment, but nothing outside the Library filters or displays by collection in this change.

## Migration Plan

1. Add the two tables, DAO, migration and migration test, with no UI. Verify saved papers and downloads survive.
2. Add domain models and repository operations, with tests for the membership rules in both directions.
3. Add `LibraryQuery` and the filtered/sorted observable read.
4. Add the Library UI: filter field, collection chips, facets, sort control.
5. Add collection management — create, rename, delete with confirmation — and paper assignment.

Rollback: steps 4–5 revert independently. Reverting steps 1–3 requires a down-migration that drops the two tables; because the tables are additive and unreferenced by any other table, that is safe and loses only collection data.

## Open Questions

- Where does assignment happen — a sheet from the card's overflow, from paper detail, or both? Both is likely right; confirm before step 5.
- Should the sort order and last facet persist across app launches? Probably yes, in preferences alongside the demo-disclosure flag; it is cheap and matches how users treat a library.
