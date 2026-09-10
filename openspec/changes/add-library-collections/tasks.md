## 1. Persistence

- [ ] 1.1 Add a `collection` entity (id, name, normalised name for uniqueness, created timestamp) and a `collection_paper` join entity keyed by `(collectionId, paperId)`.
- [ ] 1.2 Declare foreign keys with cascade delete from both the collection side and the paper side so no orphan membership can exist.
- [ ] 1.3 Add a normalised (case- and diacritic-folded) column for text matching over title and venue, populated on write, with indexes for the filter and sort columns.
- [ ] 1.4 Bump the Room schema version and write the additive migration.
- [ ] 1.5 Add a migration test asserting saved papers, cached results and download records survive the migration unchanged.
- [ ] 1.6 Add DAO operations: create, rename, delete a collection; add and remove membership; observe collections with their paper counts.

## 2. Domain and repository

- [ ] 2.1 Add a `Collection` domain model and a `LibraryQuery(text, facet, sortOrder)` type with `facet` as a sealed `All` / `InCollection` / `Unfiled` / `Downloaded`.
- [ ] 2.2 Extend `LibraryRepository` with collection operations and an observable filtered, sorted saved-papers read taking `LibraryQuery`.
- [ ] 2.3 Implement the rule that removing a paper from a collection leaves it saved, in one repository operation.
- [ ] 2.4 Implement the rule that unsaving a paper removes every membership and leaves completed download records intact, in the same place.
- [ ] 2.5 Reject blank collection names and names duplicating an existing normalised name.
- [ ] 2.6 Add tests for both membership rules from both directions, name rejection, and collection deletion leaving papers saved.

## 3. Filtering and sorting

- [ ] 3.1 Implement the filtered, ordered DAO query in SQL — never by filtering or sorting a list in the ViewModel.
- [ ] 3.2 Implement text matching over title, author names and venue against the normalised column.
- [ ] 3.3 Implement the four sort orders, ordering papers that lack the sort value last, deterministically.
- [ ] 3.4 Implement the `Downloaded` facet against completed download records only, with no reference to `AccessResolution`.
- [ ] 3.5 Add a test where a paper has a completed download and a non-actionable access state: it must appear under the downloaded facet with no access affordance rendered.
- [ ] 3.6 Add repository tests covering the matrix of text filter × facet × sort order, including combinations that match nothing.
- [ ] 3.7 Benchmark the filter query against a few thousand saved papers and record the result; open a follow-up if it is unacceptable.

## 4. Library UI

- [ ] 4.1 Add the filter-within text field to `LibraryScreen`, driving `LibraryQuery.text`.
- [ ] 4.2 Add the collection chip row with the `All`, per-collection, `Unfiled` and `Downloaded` facets, and paper counts.
- [ ] 4.3 Add the sort control with the four orders.
- [ ] 4.4 Reuse the filter-aware empty state so a filter matching nothing is distinguishable from an empty library.
- [ ] 4.5 Confirm every control meets the 48dp touch-target floor and exposes labels, roles and selected states to accessibility services.
- [ ] 4.6 Add Compose tests: filter narrows the list, facet switches it, sort reorders it, filtered-empty differs from empty library.

## 5. Collection management

- [ ] 5.1 Add create and rename surfaces with validation feedback for blank and duplicate names.
- [ ] 5.2 Add delete with a confirmation that states papers remain saved.
- [ ] 5.3 Add the assignment surface for a saved paper, allowing membership in several collections at once.
- [ ] 5.4 Decide and implement where assignment is reachable from — card overflow, paper detail, or both (see design open question).
- [ ] 5.5 Add Compose tests for create, rename, delete-with-confirmation, and multi-collection assignment.

## 6. Retention and validation

- [ ] 6.1 Extend cache maintenance so a paper referenced by a collection membership is retained, testing membership explicitly rather than relying on the saved association.
- [ ] 6.2 Add a test that prunes expired queries and asserts collections, memberships and their papers survive.
- [ ] 6.3 Confirm no remote DTO, mapper, access rule or download behaviour changed in this change.
- [ ] 6.4 Run `:app:testDebugUnitTest`, `:app:connectedDebugAndroidTest`, `:app:lint`, `:app:assembleDebug`, and the Room schema check.
- [ ] 6.5 Manually verify collections across process restart, offline, rotation, and after a cache prune.
