## Why

The Library is a single flat list of saved papers in one fixed order, with no way to group, narrow or reorder it. It works at five papers and stops working at fifty — which is the number a real user reaches within a few sessions, and the number at which the save button stops being worth pressing. Everything needed to fix it is local: the papers are already in Room, the saved association already exists, and no account, network call or backend contract is involved.

## What Changes

- **Collections.** Users can create, rename and delete named collections locally, and assign a saved paper to any number of them. Collections are user-owned data with the same durability as saved papers.
- **Filter within the library.** A text filter narrows the saved list by title, author and venue, matched locally.
- **Facet filters.** The list can be narrowed to a single collection, to papers not in any collection, and to papers with a completed download.
- **Sorting.** Recently saved, publication year, citation count and title, applied to whatever the current filters produce.
- **Removal from a collection is distinct from unsaving.** Removing a paper from a collection leaves it saved; unsaving a paper removes it from every collection.
- No change to access rendering, to the remote contract, or to what a paper is.

## Capabilities

### New Capabilities

None. This extends an existing capability rather than introducing a new one — collections are local library organisation, which is what `offline-paper-library` already owns.

### Modified Capabilities

- `offline-paper-library`: adds requirements for local collections, for filtering and sorting the saved list, and for the durability of collection membership; `Cache retention boundaries` gains collections as user-owned data that cache maintenance must not touch.

### Explicitly unchanged

- `verified-paper-access`: a collection says nothing about access, and the "downloaded" facet filters on a completed download record, never on an access state.
- `backend-ready-data-layer`: no contract change. Collections never leave the device.

> Sequencing: `offline-paper-library` is still defined by the unarchived `build-openalex-paper-reader` change, so this delta assumes that change lands (or is archived into `openspec/specs/`) first. Independent of the three UI changes, though it will conflict textually with them in `LibraryScreen.kt`.

## Impact

- **Room:** two new tables — a collection table and a paper↔collection join table with cascade on both sides — plus new DAO queries for filtered, sorted and grouped reads. Schema version bump and a migration.
- **Domain:** a `Collection` model, collection operations on `LibraryRepository`, and a library query type carrying the text filter, facet and sort order.
- **Android code:** `ui/library/LibraryScreen.kt` and `LibraryViewModel.kt` (filter row, collection chips, sort control, assignment surface), `data/local/` entities/DAOs/mappers, `data/repository/LibraryRepositoryImpl.kt`, `res/values/strings.xml`.
- **Local data:** collections and membership persist across restarts, cache pruning, and re-searches. Deleting a collection never deletes papers.
- **Tests:** DAO tests for the join and its cascades, migration test, repository tests for filter/sort combinations, Compose tests for the library surfaces.
- **Not affected:** remote DTOs, mappers, access resolution, download lifecycle, search.
