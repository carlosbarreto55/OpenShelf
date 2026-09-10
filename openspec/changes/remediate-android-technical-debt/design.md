## Context

`TECHNICAL_DEBT.md` is the authority for this change. The Android app already has Retrofit and fake remote sources, Room-backed search, WorkManager downloads, and Compose feature screens, but the live path is not a usable or continuously verified product path. The most important gaps are not isolated cleanup: build configuration, transport outcomes, pagination state, Room identity, download authorization, UI filters, fixtures, and documentation all cross module boundaries.

The current implementation confirms the register's findings:

- no manifest source set declares `INTERNET`; `BackendConfiguration.shouldUseHttp` silently falls back to fake when HTTP configuration is incomplete;
- search sends boolean false values, discards `totalCount`, persists results by `(queryKey, position)`, and represents an empty item list as terminal UI even if `nextCursor` exists;
- `HttpPaperRemoteDataSource` maps every HTTP status to generic `RepositoryError.Http`, while the fake source reports a missing paper as a contract violation;
- `DownloadsRepositoryImpl` trusts a descriptor already embedded in `Paper`, and gateway retry reuses the stored URL without calling the descriptor endpoint;
- year and work type are present in domain, request, entity, and cache-key models but absent from Compose controls;
- `refreshAccess` has no production caller; and
- `CLAUDE.md` and parts of the backend documentation describe code or OpenSpec paths that no longer represent the repository.

Several active changes touch the same surfaces. `differentiate-screen-states` already owns the intended result-count presentation, while other active proposals touch search UI, downloads UI, and Room. This change defines the complete debt-remediation behavior but implementation must incorporate already-landed equivalent tasks instead of duplicating them.

### Debt coverage

| Register items | Design area | Completion boundary |
|---|---|---|
| AND-001–003 | Deliberate build variants and network policy | Android-local |
| AND-004, AND-009 | Cursor session and transactional stable-ID deduplication | Android-local, defensive against API-005 |
| AND-005 | Typed not-found and status mapping | Android-local after agreed server status rollout |
| AND-006 | Transfer-time descriptor confirmation | Android-local after download endpoint semantics are available |
| AND-007 | Optional totals end to end | Shared with `differentiate-screen-states` |
| AND-008 | Defensive work-type aliases | Android-local, transitional after API-006 |
| AND-010 | Year/type filter controls | Android-local; server correctness covered by contract tests |
| AND-011 | Remove unused access-only refresh surface | Android-local; any server endpoint removal is separately versioned |
| AND-012 | One fake/HTTP scenario matrix | Android-local plus provider fixtures |
| AND-013 | Documentation reconciliation | Android-local |
| SHR-001–003 | Consumer suite, pinned contract version, rollout rules | Android half in scope; provider adoption is an external exit gate |

## Goals / Non-Goals

**Goals:**

- Make demo and HTTP integration intentional, separately buildable modes with no silent fallback.
- Represent the agreed API outcomes without confusing absence, not-found, malformed success, and service failure.
- Make every initial and retried production transfer depend on a newly fetched valid descriptor.
- Preserve pagination progress, stable identity, backend order, and truthful totals under imperfect but contract-valid page sequences.
- Complete the specified year/type filter experience without destabilizing query identity.
- Exercise the same semantic cases through fake and real HTTP serialization/mapping.
- Leave a documented compatibility boundary that respects long-lived installed clients.

**Non-Goals:**

- Implementing API-001–014 or changing OpenAlex behavior on the server.
- Treating metadata-derived PDF locations as safer than the server reports them; API-001 remains accepted debt.
- Calling OpenAlex, carrying an OpenAlex credential, scraping access pages, or relaxing fail-closed access rendering.
- Adding an access-only refresh UI without a product purpose.
- Adding topic-ID filters, new sort orders, accounts, sync, or unrelated UI proposals.
- Claiming SHR-001–003 complete until provider-side CI consumes the same contract bundle.

## Decisions

### 1. Use `demo` and `integration` product flavors; never infer a live mode

Add an environment flavor dimension:

- `demo` fixes the remote mode to fake, requires no backend URL, keeps the fake download gateway, and is the documented default for demonstrations.
- `integration` fixes the remote mode to HTTP and requires a syntactically valid PaperReader base URL at build/startup. Missing or invalid configuration fails explicitly instead of selecting fake behavior.

The backend URL remains a non-secret Gradle/CI input. The OpenAlex credential remains forbidden. `INTERNET` belongs in the integration flavor manifest so the demo APK does not request a permission it does not use.

`integrationDebug` receives a debug-only network-security config permitting cleartext only for emulator/ADB-loopback development hosts (`10.0.2.2` and `localhost`). Other device targets use HTTPS. `integrationRelease` has no cleartext exception, validates an HTTPS base URL, and relies on the platform default that rejects cleartext traffic.

Alternative considered: keep a single build and switch using `paperReaderRemoteMode`. Rejected because an omitted URL currently changes runtime behavior silently and lets CI exercise only the fake path while appearing integration-ready.

### 2. Model business outcomes before mapping them into screens

Add a first-class `RepositoryError.NotFound` (or equivalently named non-recoverable domain error) and map HTTP 404 to it. Map HTTP 504 to the timeout outcome and 502 to a recoverable upstream/service-unavailable outcome; other HTTP statuses retain their status without exposing response bodies. A malformed 2xx body remains `Contract`, never not-found.

The download descriptor operation returns an explicit domain availability value:

- `Available(descriptor)` for a valid, matching response;
- `Unavailable` for `200` with the requested `paper_id` and `descriptor: null`;
- `Failure(NotFound)` for 404;
- typed failures for 502, 504, offline, ID mismatch, malformed body, or invalid/expired descriptor.

Paper detail maps not-found to a stable not-found content state rather than a generic retry screen. Search no matches remains `200` plus an empty terminal page and is not an error.

`refreshAccess` is removed from the Android Retrofit/data-source/repository surface and its unused request/response mapping code. Full paper refresh remains the detail refresh path, cached actionable states still expire conservatively, and the download endpoint supplies transfer-time freshness. The server may retain `/access:refresh`; removing or repurposing that endpoint is outside this Android change and must follow API versioning policy.

Alternative considered: add an explicit access-refresh button solely to create a call site. Rejected because the register records no product purpose for a second refresh path, and UI for `CHECKING`/`ERROR` could conflict with the established silent-access requirements.

### 3. Re-confirm descriptors at the repository boundary and pass the fresh value into retry

Replace `PaperRepository.refreshAccess` with a descriptor-confirmation operation (or a focused collaborator behind the same domain boundary). `DownloadsRepositoryImpl` performs confirmation immediately before both `enqueue` and `retry`:

1. For a new transfer, retain the fail-closed prerequisite that the action came from a current `DirectPdf` paper.
2. Call `GET /v1/papers/{paperId}/download`.
3. Require a matching paper ID and a valid HTTPS descriptor; when an expiry exists, it must be later than the injected clock's current time.
4. Only then call the gateway.
5. Change `DownloadGateway.retry` to accept the confirmed descriptor and atomically replace the stored `sourceUrl`/declared MIME metadata before scheduling work.

Retry confirmation does not depend on the cached paper still containing an actionable descriptor; the endpoint is the freshness authority. Offline, timeout, no-PDF, and not-found leave the failed/canceled record and any completed file state intact and enqueue no work. Per-paper unique WorkManager work remains the final duplication guard.

Download-confirmation failures are not discarded by ViewModels. Search/detail download actions and Downloads-screen retry expose safe, accessible feedback appropriate to no-PDF, not-found, offline, timeout, service unavailable, or validation failure; no raw endpoint URL or response body reaches presentation state.

Alternative considered: refresh the full paper and reuse its embedded descriptor. Rejected because decision 5 in the register specifically assigns confirmation to the download endpoint, which has narrower and clearer semantics.

### 4. Treat cursor traversal as a bounded session independent of visible item count

Move empty-page handling below Compose into the search orchestration/repository boundary so a valid later page does not flash a terminal empty state. For each first-page or append action:

- track every requested/returned cursor in the active query session;
- stop immediately with a contract failure if a cursor repeats or does not advance;
- automatically traverse at most three consecutive empty pages while `next_cursor` remains;
- publish/persist the final non-empty page or terminal page as one logical operation;
- if the bound is reached and another cursor remains, retain that cursor and expose a non-terminal empty-page retry state rather than claiming there are no matches.

Changing query text, filters, or sort creates a new cursor session and cancels the old one. An append failure retains already cached items and its retry cursor. This defensive bound complements but does not replace the server aggregation required by API-005.

Alternative considered: let Compose call `loadNextPage` whenever an empty list has a cursor. Rejected because UI recomposition is a poor loop controller and can issue duplicate requests or spin indefinitely.

### 5. Enforce one `(queryKey, paperId)` and preserve first-seen order transactionally

Add a unique database index over `(queryKey, paperId)`. The migration removes any existing duplicate associations by retaining the lowest position before creating the index; it never deletes the underlying paper, saved state, access snapshot, or download record.

Page persistence runs in one Room transaction. It deduplicates within the incoming page, excludes IDs already associated with the query, preserves the first occurrence and server order, assigns new positions after the current maximum position, updates the query cursor/total, and upserts paper/access data. Do not use result count as the next position because migrated rows can have gaps.

On first-page replacement, prior associations for that query are cleared before the deduplicated first page is inserted. Compose can then safely continue using paper ID as its stable item key.

Alternative considered: deduplicate only the observed Kotlin list. Rejected because duplicates would remain in Room, reappear after restart, and still make cache size/position semantics incorrect.

### 6. Keep totals optional and stable across append operations

Complete the path already started in the code and planned by `differentiate-screen-states`: `PaperSearchResponseDto.totalCount` → `CursorPage.totalItems` → `SearchQueryEntity.totalCount` → `SearchContent.Loaded` → count copy.

A new first page replaces the previously stored total, including clearing it when absent. An append response with a non-null total updates the stored value; an append response that omits the field preserves the current query's known total. The client never derives a total from loaded rows, pages, or upstream-looking values. Copy distinguishes “showing N” from “showing N of M”. Invalid negative totals are treated as absent/contract-invalid according to the canonical fixture decision, never displayed.

If `differentiate-screen-states` lands first, its equivalent implementation tasks are verified and marked satisfied here; this change only adds missing persistence/contract edge cases.

### 7. Centralize work-type wire normalization and complete the filter controls

Give each supported `PaperType` one canonical outbound wire value and one parser that is case-insensitive and accepts the documented legacy `book-chapter` alias (along with the already supported canonical `book_chapter`). Blank/null remains `UNKNOWN`; unknown non-blank values remain `OTHER`. Requests emit canonical values only.

Inactive optional boolean restrictions are omitted from HTTP requests; an enabled restriction is sent as `true`. This keeps Android aligned with the recorded meaning that false and omission are both unrestricted, and avoids depending on an older server's incorrect `open_access=false` translation. Topic values remain normalized free text and are never converted to OpenAlex IDs.

Add accessible search controls for one exact publication year and one supported work type (`article`, `review`, `preprint`, `book_chapter`, or `dataset`). The year control validates the same documented contract range used by mapping; each control exposes selected state and an individual clear action. Existing `PaperFilters`, debounced query orchestration, request mapping, and `toCacheKey` remain the single state path, so changing/clearing either filter starts a new first-page cursor session and sorting preserves both filters.

Alternative considered: accept arbitrary type text in the UI. Rejected because it creates avoidable unsupported values while the public contract already publishes a finite set.

### 8. Test semantics through a pinned v1 consumer bundle, not hand-built API fakes

Add a versioned Android test-resource bundle containing representative v1 request expectations, response/error fixtures, and a manifest recording the upstream canonical contract version and checksum. Use Retrofit plus a real HTTP test server (for example OkHttp MockWebServer as a test-only dependency) so query encoding, status handling, JSON serialization, nullability, and mapping are exercised together.

Run one reusable semantic scenario matrix against the HTTP source and fake source for: normal and empty search, open-access false/true request meaning, free-text topic, supported and legacy work types, empty cursor pages, duplicate IDs, totals present/absent, detail/download not-found, no-PDF, 502, 504, malformed success, mismatched ID, and invalid access/descriptor data.

The provider remains responsible for publishing and verifying the same canonical bundle. Android CI pins a version and fails on unreviewed drift. Within `/v1`, changes must be additive: optional fields may be added, known enum values may be added only where clients safely fall back, existing meanings/nullability cannot change, and fields/endpoints cannot be removed during the support window. Breaking behavior requires `/v2`; v1 remains available for the documented mobile deprecation window.

Alternative considered: expand the current `FakeApi` unit double. Rejected because it bypasses Retrofit, HTTP status processing, and serialization—the exact seams where the register found drift.

### 9. Documentation is updated from verified state, last

After behavior and tests land, reconcile `README.md`, `CLAUDE.md`, `docs/architecture.md`, and `docs/backend/*`. Documentation must label demo-only behavior, integration commands, current limitations, accepted debt, server rollout dependencies, and active proposals. Links must target archived/main specs rather than deleted change paths. `TECHNICAL_DEBT.md` items are closed only when their exit criteria and tests are satisfied; a green legacy suite alone is insufficient.

## Risks / Trade-offs

- [Several active changes modify the same Room and UI files] → Land `differentiate-screen-states` first for totals, choose one schema version sequence, and rebase each remaining proposal before implementation rather than copying stale task assumptions.
- [A debug cleartext exception leaks into release] → Keep it in the `integrationDebug` source set, add merged-manifest tests for both variants, and reject non-HTTPS integration-release URLs.
- [Integration misconfiguration silently shows demo data] → Flavor-hardcode the mode and fail fast on missing/invalid integration URLs; retain an obvious mode disclosure.
- [Automatic empty-page traversal amplifies requests] → Cap it at three per action, detect repeated cursors, cancel with the active query, and rely on API-005 for the normal server-side aggregation path.
- [Adding a unique index fails on existing duplicate cache rows] → Delete only duplicate cross-references while retaining the earliest position before creating the index; cover migration with real v2 data containing overlaps.
- [Descriptor changes between confirmation and worker execution] → Confirmation is immediate and expiry-aware, but not atomic with an external server; the worker still validates HTTPS response and PDF content and reports failure safely.
- [Pinned fixtures become another source of truth] → Record version/checksum and automate comparison with the provider-published canonical bundle; Android-authored prose becomes consumer guidance, not a competing schema.
- [Installed clients cannot roll back a Room schema bump] → Use an additive forward migration, never destructive fallback for user-owned data, and roll back live integration through the build/server gate rather than downgrading the database.
- [Removing access refresh hides a later product requirement] → The server endpoint may remain; reintroducing a client call requires an explicit product flow and a new capability delta rather than preserving dead code indefinitely.
- [The server rollout is incomplete] → Keep demo usable, gate live release on the v1 provider matrix, and do not mark shared or blocked debt closed from Android-only tests.

## Migration Plan

1. Reconcile this change with active proposals: land/incorporate `differentiate-screen-states`, select the next Room schema version, and update overlapping tasks without losing their requirements.
2. Add the pinned v1 consumer fixtures and HTTP harness first; encode current failures as tests before changing production mappings.
3. Introduce demo/integration flavors, flavor manifests, strict configuration validation, and build/merged-manifest checks. Keep demo as the safe operational fallback.
4. Add typed not-found/download-availability outcomes, 502/504 mappings, fake parity cases, canonical/legacy work-type parsing, and remove the unused access-refresh client surface.
5. Implement bounded cursor traversal and transactional stable-ID deduplication; migrate existing cache associations and verify saved/download data survives.
6. Complete optional total propagation, preserving or clearing totals according to page position and field presence.
7. Rework download start/retry around fresh descriptor confirmation and update gateways to consume the refreshed descriptor.
8. Add publication-year and work-type controls, accessibility/state tests, and cache-key/request assertions.
9. Run JVM, Room migration, lint, demo/integration assembly, merged-manifest, instrumentation, and controlled Android-to-API/PDF transfer checks.
10. Update documentation and the debt register only for criteria proven by the completed checks. Enable live release only after the provider passes the same v1 contract bundle.

Rollback keeps the demo flavor available and disables distribution of the integration variant. Database changes remain forward-migrated; no rollback path may drop user-owned saved/download data.

## Open Questions

- Where the server team will publish the canonical v1 bundle and what CI authentication/distribution mechanism Android will use must be agreed before SHR-001/SHR-002 can close. This does not block Android's pinned consumer harness.
- The supported v1 deprecation window and minimum mobile-version policy require release-management agreement before SHR-003 can close. Until then, the conservative rule is that released v1 clients remain supported and no breaking v1 change is allowed.
