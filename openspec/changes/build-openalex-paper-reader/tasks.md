## 1. Android and Compose Foundation

- [x] 1.1 Add Kotlin Android, Compose, KSP, serialization, dependency-injection, Room, lifecycle, navigation, coroutines, networking, Paging 3, WorkManager, and test dependencies to the version catalog and Gradle files.
- [x] 1.2 Enable Compose and Kotlin/JVM compiler settings compatible with the existing minSdk 26 and compile/target SDK 36 configuration.
- [ ] 1.3 Add the application class, manifest wiring, single Compose activity, edge-to-edge window setup, and a minimal app root that builds and launches.
- [ ] 1.4 Implement Material 3 color, type, shape, spacing, and light/dark theme tokens derived from `docs/ui/lattice-search-reference.png`.
- [ ] 1.5 Add typed navigation routes and the Search, Library, and Downloads bottom navigation shell with placeholder destinations.
- [ ] 1.6 Add shared Compose components for loading, empty, recoverable error, stale-data notice, and accessible icon/button semantics.

## 2. Domain and Backend Interaction Contracts

- [ ] 2.1 Define stable domain models for papers, authors, topics, queries, filters, sort order, cursor pages, saved state, access resolution, and download state.
- [ ] 2.2 Define normalized serializable request/response DTOs for backend search, paper details, access refresh, and confirmed PDF download descriptors.
- [ ] 2.3 Define `PaperRemoteDataSource`, `PaperRepository`, `LibraryRepository`, and download gateway interfaces without exposing transport or Room types to ViewModels.
- [ ] 2.4 Implement DTO validation and mappers that reject invalid paper identity, tolerate unknown optional fields, and fail closed for unknown or malformed access descriptors.
- [ ] 2.5 Add compile-time backend configuration and dependency-injection bindings that select a fake remote source by default and allow a future HTTP source without changing UI/domain code.
- [ ] 2.6 Scaffold the inactive HTTP service and remote data-source implementation against a configurable PaperReader backend base URL, with no OpenAlex URL or API key embedded in the Android project.
- [ ] 2.7 Add a repository error taxonomy for offline, timeout, HTTP, contract, access-validation, and unexpected failures, including safe user-facing mappings.

## 3. Placeholder Backend Experience

- [ ] 3.1 Add deterministic JSON fixtures using the production DTO schema, including confirmed direct PDF, confirmed open HTML, no-free-full-text, paywalled, unknown, missing optional metadata, empty, and paginated cases.
- [ ] 3.2 Implement the fake remote data source with configurable latency, cursor pages, filter/sort behavior, and injectable failures so all screen states are demonstrable.
- [ ] 3.3 Route fixture responses through real serialization, mapping, repositories, and Room before exposing results to the UI; do not return hard-coded UI models from ViewModels.
- [ ] 3.4 Add a debug/demo-data indicator and documentation that clearly distinguish placeholder responses from a live OpenAlex/backend integration.
- [ ] 3.5 Add tests proving the fake and HTTP data sources satisfy the same interface and fixture payloads remain compatible with the DTO contract.

## 4. Room Persistence and Repository Coordination

- [ ] 4.1 Define Room entities, relations/cross-references, type converters, and stable keys for papers, ordered query results, recent searches, saved papers, access snapshots, and download records.
- [ ] 4.2 Implement DAOs with observable queries for discovery results, paper details, recent searches, library contents, access state, and downloads.
- [ ] 4.3 Create the versioned Room database, export its schema for tests, and add initial database/in-memory test configuration.
- [ ] 4.4 Implement transactional search-page persistence that deduplicates papers, preserves backend order/cursors, and protects saved/download-owned records.
- [ ] 4.5 Implement repository cache policy for cached-first observation, refresh, stale timestamps, offline fallback, and access-snapshot expiry.
- [ ] 4.6 Implement bounded/deduplicated recent-query storage plus independent clear-recents and stale-cache-pruning operations.
- [ ] 4.7 Add DAO and repository tests for atomic result replacement, multi-query paper reuse, save-state preservation, access expiry, and safe cache pruning.

## 5. Search and Discovery UI

- [ ] 5.1 Implement the search use cases and ViewModel with immutable state, debounced query updates, stale-response cancellation, retry, refresh, filter, sort, and pagination events.
- [ ] 5.2 Build the Lattice-style search header, hero, search field, filter chips, result count, and sort control with previews for light/dark and scaled-text configurations.
- [ ] 5.3 Build stable-key lazy paper cards containing title, compact author/venue text, year, type, citations, topic, and save affordance while gracefully omitting missing metadata.
- [ ] 5.4 Centralize access-to-UI mapping and render Download PDF only for valid `DIRECT_PDF`, Read online only for valid `OPEN_FULL_TEXT`, and no access-related element for every other state.
- [ ] 5.5 Implement initial, loading, content, empty, stale-cache, first-page error, append-loading, and append-error presentations without discarding existing content.
- [ ] 5.6 Connect cursor pagination through Paging 3 or the chosen equivalent and verify unique ordered append behavior.
- [ ] 5.7 Add Compose/ViewModel tests for query debounce, filter/sort retention, late-response suppression, empty/retry behavior, and inaccessible-card semantics.

## 6. Paper Details and Local Library

- [ ] 6.1 Implement paper-detail repository/use-case and ViewModel flows that observe Room, request refresh through the remote abstraction, and retain cached content on failure.
- [ ] 6.2 Build the paper-detail screen for complete authors, venue/year/type/citations/topics, abstract, save state, and the same centralized verified-access actions used by cards.
- [ ] 6.3 Implement save/unsave use cases and optimistic-safe ViewModel updates backed by Room transactions.
- [ ] 6.4 Build the Library screen with saved-paper list, empty state, detail navigation, and no account or synchronization affordances.
- [ ] 6.5 Add tests proving saved state stays consistent across search/detail/library and inaccessible saved papers expose no access UI.

## 7. Download Lifecycle

- [ ] 7.1 Define the production download scheduler/manager boundary and a WorkManager-based worker contract that can stream only a valid confirmed descriptor into app-managed storage.
- [ ] 7.2 Implement response checks for successful status, non-empty content, PDF MIME hints, and `%PDF-` file signature before a production download can become completed.
- [ ] 7.3 Implement a fake download manager that simulates queue/progress/failure/cancel/retry through persisted download records and never creates a fabricated paper PDF.
- [ ] 7.4 Implement the downloads repository and ViewModel with observable queued, running, completed, failed, and canceled state plus retry/cancel events.
- [ ] 7.5 Build the Downloads screen and connect Download PDF actions so search, detail, and downloads observe one persistent lifecycle.
- [ ] 7.6 Configure a secure `FileProvider` path and completed-file open intent with a user-visible fallback when no compatible PDF viewer exists.
- [ ] 7.7 Add tests for verified-download gating, simulated progress, retry/cancel cleanup, restart reconciliation, invalid HTML-as-PDF rejection, and content-URI opening behavior.

## 8. Access, Security, and Accessibility Verification

- [ ] 8.1 Add unit tests covering every access enum and malformed descriptor to prove only confirmed PDF/open HTML states produce actions.
- [ ] 8.2 Add Compose semantics tests proving no badge, source URL, lock, status label, or action exists for no-free, paywalled, unknown, checking, and error states on all relevant screens.
- [ ] 8.3 Add a repository-wide secret scan/check that fails if the supplied OpenAlex key is present in source, resources, build configuration, fixtures, logs, or packaged artifacts.
- [ ] 8.4 Audit touch targets, content descriptions, selected chip/navigation state, screen-reader order, contrast, and large-font reflow against supported phone widths.
- [ ] 8.5 Add representative screenshot tests for the UI reference state, confirmed PDF, confirmed open HTML, metadata-only paper, loading, empty, and error states.

## 9. Documentation and Final Validation

- [ ] 9.1 Document the module/package structure, MVVM data flow, Room schema ownership, fake-mode behavior, and steps for adding a real backend base URL and binding later.
- [ ] 9.2 Document the proposed normalized backend endpoints and access-state contract, including the rule that the server inspects all locations and that scraping/PDF generation remains server-side.
- [ ] 9.3 Add a README walkthrough with screenshots, demo scenarios, architecture highlights, limitations, and an explicit statement that current data/download behavior is placeholder-backed.
- [ ] 9.4 Run formatting/static analysis, unit tests, instrumentation/Compose tests, Room schema checks, and `:app:assembleDebug`; resolve all failures.
- [ ] 9.5 Manually verify search, filters, sorting, detail, save/library, offline cache, all access variants, download lifecycle, dark theme, rotation, and process restart using the demo source.
