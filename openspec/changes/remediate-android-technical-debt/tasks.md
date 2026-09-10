## 1. Reconcile the baseline and overlapping changes

- [x] 1.1 Capture the current unit-test, lint, schema, and assembly baseline without treating existing green tests as closing any debt item.
- [x] 1.2 Compare this change with every active OpenSpec change touching `SearchScreen`, download UI, or Room and record the implementation/merge order before editing shared files.
- [x] 1.3 Land or incorporate the result-total work from `differentiate-screen-states`; mark equivalent tasks here satisfied only after this change's total edge cases pass.
- [x] 1.4 Select one forward Room schema-version sequence for this change and the active collections/reader proposals so no migration number or exported schema is overwritten.
- [x] 1.5 Create a verification matrix mapping AND-001–013 and Android's portion of SHR-001–003 to the exact automated/manual checks and external gates required for closure.

## 2. Build the Android consumer-contract harness

- [x] 2.1 Add the test-only HTTP server dependency needed to exercise Retrofit serialization and real HTTP status handling.
- [x] 2.2 Add a `v1` consumer-contract test-resource bundle with request expectations, success/error fixtures, upstream version/provenance, and a recorded checksum.
- [x] 2.3 Add a deterministic verification task/test that fails when the pinned contract resources no longer match their manifest checksum.
- [x] 2.4 Build a test Retrofit instance against the local HTTP server using the production `RemoteJson` converter and `PaperReaderApi` interface.
- [x] 2.5 Add request tests for omitted inactive boolean filters, enabled `true` filters, free-text topic, canonical work type, cursor, sort, and publication year.
- [x] 2.6 Add response tests for normal/empty search, details, descriptor available/unavailable, totals present/absent, unknown optional fields, and supported/legacy/unknown work types.
- [x] 2.7 Add HTTP outcome tests for 404, 502, 504, malformed 2xx JSON, null/missing required success fields, and mismatched response IDs without reading raw error bodies into UI state.
- [x] 2.8 Add access-contract fixtures for every access state plus invalid HTTPS descriptors/source URLs and prove every uncertain or malformed case remains non-actionable.
- [x] 2.9 Add selectable fake-source scenarios for not-found, no-PDF, empty cursor pages, 502-equivalent unavailable, 504-equivalent timeout, mismatched identity, and malformed data.
- [x] 2.10 Run one reusable repository-level expectation matrix against fake and HTTP sources and eliminate mode-specific outcomes above the remote boundary.
- [x] 2.11 Add the Android CI contract gate and document that SHR-001/SHR-002 remain open until provider CI verifies the same canonical bundle/version.

## 3. Add deliberate demo and integration builds

- [x] 3.1 Add an environment flavor dimension with `demo` and `integration` product flavors and update variant-specific test/source-set configuration.
- [x] 3.2 Hardcode fake versus HTTP mode in flavor BuildConfig fields; keep only the PaperReader backend URL as an external Gradle/CI input.
- [x] 3.3 Validate integration URLs with actionable errors, require an HTTPS URL for integration-release, and remove `shouldUseHttp` behavior that silently falls back to fake.
- [x] 3.4 Update backend and download Hilt selection so each flavor chooses exactly one matching remote/gateway implementation.
- [x] 3.5 Add `android.permission.INTERNET` to the integration flavor manifest and verify the demo variant remains fully functional without backend network calls.
- [x] 3.6 Add an `integrationDebug` network-security config and manifest overlay allowing cleartext only for documented emulator/ADB-loopback hosts.
- [x] 3.7 Verify the integration-release merged manifest and packaged resources contain no cleartext exception.
- [x] 3.8 Add configuration and dependency-selection tests for valid demo, valid integration, missing URL, invalid URL, debug HTTP, and release HTTP cases.
- [x] 3.9 Add merged-manifest tests/assertions proving the integration permission/debug policy and the release HTTPS boundary.
- [x] 3.10 Add a controlled integration-debug instrumentation path that reaches a local search endpoint and completes a validated PDF transfer.
- [x] 3.11 Add local/CI aggregate commands that assemble and test both flavors and run the existing source/artifact OpenAlex-secret scans.

## 4. Align domain, HTTP, and fake outcomes

- [x] 4.1 Add first-class non-recoverable not-found and recoverable upstream-unavailable outcomes to the domain error taxonomy with safe user messages.
- [x] 4.2 Add an explicit descriptor-availability domain type that distinguishes a valid descriptor from the normal no-PDF case.
- [x] 4.3 Map HTTP 404 to not-found, HTTP 502 to upstream unavailable, and HTTP 504 to timeout while retaining safe handling for other 4xx/5xx responses.
- [x] 4.4 Update detail and descriptor mappers so malformed 2xx data, mismatched IDs, not-found, and valid null descriptors remain distinct outcomes.
- [x] 4.5 Change HTTP query encoding to omit inactive optional boolean restrictions and send only canonical active values.
- [x] 4.6 Give supported `PaperType` values explicit canonical wire values and use them for requests instead of deriving wire text ad hoc from enum names.
- [x] 4.7 Extend work-type parsing for case-insensitive canonical values and the documented `book-chapter` alias while preserving blank→`UNKNOWN` and unknown→`OTHER`.
- [x] 4.8 Remove the unused access-refresh operation and DTO/mapping surface from Android API, remote, repository, fakes, and test doubles without changing fail-closed cache behavior.
- [x] 4.9 Make fake missing-paper, no-PDF, timeout, unavailable, malformed, and mismatch scenarios produce the same typed outcomes as HTTP.
- [x] 4.10 Update exhaustive error presentation mappings and detail state to render not-found distinctly and keep 502/504 recoverable.
- [x] 4.11 Add mapper, remote-source, repository, and detail-ViewModel tests for every new/changed outcome.

## 5. Re-confirm every initial and retried download

- [x] 5.1 Add a repository/domain operation that fetches and maps download availability for a stable paper ID through `PaperRemoteDataSource.getDownloadDescriptor`.
- [x] 5.2 Validate a returned descriptor's response ID, HTTPS URL, structural fields, and optional expiry against the injected clock before authorizing a transfer.
- [x] 5.3 Keep the current confirmed-`DirectPdf` prerequisite for a new user action, then call the descriptor endpoint immediately before gateway enqueue.
- [x] 5.4 Change retry to call the descriptor endpoint by the persisted paper ID even when its cached access snapshot is stale or non-actionable.
- [x] 5.5 Change `DownloadGateway.retry` to accept a freshly confirmed descriptor.
- [x] 5.6 Update `WorkManagerDownloadGateway` to replace the stored source URL/declared MIME data before scheduling retried work.
- [x] 5.7 Update `FakeDownloadManager` and gateway test doubles to accept the fresh retry descriptor while retaining honest simulated-file behavior.
- [x] 5.8 Ensure no-PDF, not-found, offline, timeout, upstream-unavailable, mismatch, malformed, and expired results enqueue no work and preserve the existing download record/file state.
- [x] 5.9 Propagate start/retry confirmation results into safe accessible feedback on search, detail, and Downloads surfaces instead of discarding repository failures.
- [x] 5.10 Add initial-download tests for fresh success and every non-enqueueing outcome, including an assertion that a cached URL is never used as fallback.
- [x] 5.11 Add retry tests proving confirmation occurs on every attempt and a changed server URL replaces the persisted/gateway URL.
- [x] 5.12 Add WorkManager and fake-gateway tests proving confirmation happens before scheduling and duplicate taps still resolve to one per-paper work chain.

## 6. Enforce stable cached result identity

- [x] 6.1 Add a unique `(queryKey, paperId)` index to `SearchResultEntity` using the schema version selected in task 1.4.
- [x] 6.2 Write a forward migration that keeps the lowest-position association for existing duplicates before creating the unique index.
- [x] 6.3 Replace result-count position allocation with a DAO query for the current maximum position so migrated gaps cannot reorder appends.
- [x] 6.4 Add DAO support for reading already-associated paper IDs and deduplicate an incoming page by first occurrence before assigning positions.
- [x] 6.5 Persist query metadata, paper rows, access snapshots, and result associations inside one Room transaction for first-page replacement and append.
- [x] 6.6 Ensure first-page replacement clears only that query's old associations and inserts its deduplicated new order.
- [x] 6.7 Ensure append keeps existing positions, ignores repeated IDs, and appends unseen IDs in backend order while still advancing cursor metadata.
- [x] 6.8 Add DAO/repository tests for duplicates within one page, overlaps across pages, overlap-only pages, order after restart, and concurrent/repeated append attempts.
- [x] 6.9 Add a real migration test seeded with duplicate cached associations and prove paper, access, saved, and download rows survive unchanged.
- [x] 6.10 Export and review the new Room schema, including both the unique index and any total column incorporated from the overlapping change.

## 7. Make cursor traversal bounded and non-terminal across empty pages

- [x] 7.1 Introduce a query-scoped cursor session below Compose that tracks requested and returned cursors and is reset when query/filter/sort changes.
- [x] 7.2 Automatically request up to three consecutive empty pages when each response supplies a new next cursor.
- [x] 7.3 Detect a repeated or non-advancing cursor before issuing another request and return a typed pagination contract failure.
- [x] 7.4 Coalesce automatically traversed empty pages into one logical persistence/UI update so a later non-empty page does not flash a terminal empty state.
- [x] 7.5 Represent bound exhaustion with a retained cursor and non-terminal retry state; reserve terminal empty content for a page sequence with no next cursor.
- [x] 7.6 Preserve already loaded items, cursor retryability, and append error feedback when an automatic or user-triggered continuation fails.
- [x] 7.7 Cancel the old cursor session and suppress its late responses when query text, filters, or sort changes.
- [x] 7.8 Add tests for empty-first→non-empty, empty-append→non-empty, terminal empty, three-page bound exhaustion, repeated cursor, overlap-only page, and late old-query response.

## 8. Complete optional total propagation   

- [x] 8.1 Add or verify `CursorPage.totalItems: Int?` and carry `PaperSearchResponseDto.totalCount` through mapping without fabricating absent or invalid values.
- [x] 8.2 Add or verify the cached query total column and incorporate it into the schema/migration selected in task 1.4 rather than creating a conflicting migration.
- [x] 8.3 On first-page replacement, store the supplied total or clear a previously cached total when the new response omits it.
- [x] 8.4 On append, accept a newly supplied total but preserve the current known total when the append response omits it.
- [x] 8.5 Emit the cached optional total from `observeSearch` and carry it through `SearchContent.Loaded`.
- [x] 8.6 Integrate the `differentiate-screen-states` count presentation so copy says “showing N” or “showing N of M” using unique visible items.
- [x] 8.7 Add mapper/repository/ViewModel/UI tests for totals present/absent, first-page refresh clearing, append omission preservation, append update, deduplicated shown count, and offline cached display.

## 9. Expose publication-year and work-type filters

- [x] 9.1 Define one reusable supported-year validation rule consistent with the remote contract and expose safe validation text for out-of-range input.
- [x] 9.2 Add an accessible exact-year control that displays its current selection, applies only valid input, and supports individual clearing.
- [x] 9.3 Add an accessible work-type control limited to the five canonical public values with selected-state semantics and individual clearing.
- [x] 9.4 Route both controls through the existing `PaperFilters` state so changes trigger a new first page and active values survive sorting and pagination.
- [x] 9.5 Keep “clear all” behavior exhaustive and ensure filter-aware empty-state code from `differentiate-screen-states` names/removes the new filters.
- [x] 9.6 Verify request mapping and normalized cache keys distinguish year/type combinations and emit canonical `book_chapter`.
- [x] 9.7 Add ViewModel tests for apply, clear-one, clear-all, sort preservation, pagination preservation, invalid year, and old-response cancellation.
- [x] 9.8 Add Compose semantics and large-font tests for both controls, including keyboard/screen-reader operation and narrow phone layouts.

## 10. Documentation, final verification, and rollout gates

- [x] 10.1 Update `README.md` with demo/integration commands, local emulator/ADB networking, HTTPS release requirements, honest live-backend status, and the controlled E2E workflow.
- [x] 10.2 Replace stale `CLAUDE.md` implementation status and deleted OpenSpec paths with the current architecture, active changes, main specs, and debt-register workflow.
- [x] 10.3 Reconcile `docs/architecture.md` and `docs/backend/*` with explicit build modes, supported Android endpoints, request/error semantics, descriptor confirmation, contract versioning, and accepted PDF-verification debt.
- [x] 10.4 Add a compatibility/rollout document naming the pinned v1 bundle, additive-change rules, Android support assumptions, provider CI gate, and unresolved deprecation-window owner.
- [x] 10.5 Run the complete demo and integration JVM suites, Room migration tests, consumer/parity contract suite, Android lint, and both debug/release assemblies.
- [x] 10.6 Inspect merged manifests and packaged artifacts for Internet/cleartext boundaries and rerun the source/APK OpenAlex-secret checks.
- [x] 10.7 Run connected Compose/instrumentation tests for detail not-found, filter accessibility, and a controlled live-mode search/PDF transfer on an emulator or device.
- [x] 10.8 Run the provider suite against the identical canonical contract version; if provider access is unavailable or failures remain, keep live release and SHR-001–003 closure blocked while preserving Android evidence.
- [ ] 10.9 Manually verify demo mode, misconfigured integration failure, local HTTP debug, HTTPS release configuration, empty-page continuation, overlapping pagination, total copy, filter combinations, and download reconfirmation failures.
- [x] 10.10 Update `TECHNICAL_DEBT.md` statuses only for items whose exit criteria and evidence are complete; retain server-dependent/shared items with their external blocker.
