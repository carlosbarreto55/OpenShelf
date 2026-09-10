# PaperReader Technical Debt Register

Last reviewed: 2026-09-09  
Scope: `PaperReaderApi` (server) and `PaperReader` (Android client)

This document is the authoritative register of known technical debt and contract gaps across the
PaperReader server and Android application. It records ownership and the business decisions that
govern later implementation. An item stays open until its exit criteria are satisfied, even if a
local unit test already covers the current behavior.

## Status and priority

- **Accepted debt**: the limitation is understood and intentionally remains for now.
- **Open**: confirmed work with an assigned owner.
- **Blocked by shared rollout**: server and client changes must be coordinated.
- **Needs decision**: behavior is not yet sufficiently defined to implement safely.
- **P0**: prevents or materially invalidates live integration.
- **P1**: breaks a business rule or produces materially incorrect behavior.
- **P2**: resilience, maintainability, incomplete product behavior, or documentation debt.

## Recorded business decisions

These decisions are final unless explicitly revisited:

1. **PDF verification is accepted technical debt.** The server may derive `DIRECT_PDF` from
   OpenAlex metadata for now. Full backend verification of candidate PDF locations will be added
   later; it is not part of the immediate contract-alignment work.
2. **`topic` is free text.** The public `topic` search parameter is not an OpenAlex topic ID.
3. **Not-found and upstream failures use HTTP semantics:**
    - Search with no matches: `200` with an empty `items` list.
    - Unknown paper on detail, access refresh, or download: `404`.
    - Existing paper with no downloadable PDF: `200` with the requested `paper_id` and
      `descriptor: null`.
    - OpenAlex/upstream unavailable: `502`.
    - OpenAlex/upstream timeout: `504`.
4. **The server owns PDF-filter pagination aggregation.** For `pdf_available=true`, the server
   will consume bounded upstream pages to produce a useful client page instead of returning an
   immediately terminal-looking empty page while more results are available.
5. **The client re-confirms downloads.** Android will call `GET /v1/papers/{paperId}/download`
   immediately before starting or retrying a download rather than enqueueing solely from a cached
   descriptor embedded in `Paper`.

## Immediate ownership summary

### Server-owned

- Correct search/filter semantics and normalized response values.
- Implement the agreed free-text topic behavior.
- Implement the agreed HTTP not-found/upstream policy.
- Aggregate bounded upstream pages for the PDF-only filter.
- Return truthful totals and preserve valid fallback access candidates.
- Eventually verify PDF access server-side.

### Android-owned

- Enable network access and provide a deliberate live-backend build configuration.
- Consume the agreed HTTP not-found/upstream behavior.
- Re-confirm descriptors through the download endpoint before starting or retrying.
- Preserve pagination, totals, and result identity defensively.
- Complete the filter UI promised by the product specification.

### Shared

- Maintain one versioned contract and run consumer/provider contract tests in both repositories.
- Coordinate breaking response-policy changes so neither application is deployed against an
  incompatible version.

## Server/API technical debt

### API-001 — PDF availability is metadata-derived, not verified

- **Priority:** P0 business risk
- **Status:** Accepted debt
- **Owner:** Server
- **Current behavior:** `best_oa_location.pdf_url` is promoted to `DIRECT_PDF` when its URL is
  structurally valid. The server does not inspect all candidate locations and does not verify the
  remote response.
- **Risk:** Dead links, access-controlled responses, redirect traps, or HTML landing pages can be
  represented as confirmed PDFs. Android trusts the normalized decision and may expose a download
  action.
- **Planned resolution:** Implement backend-owned verification across eligible OpenAlex candidate
  locations. Verification must include redirect handling, HTTPS enforcement, SSRF protections,
  bounded response work, and sufficient content checks to distinguish a PDF from HTML or an error
  document. Preserve provenance and indicate generated content accurately.
- **Exit criteria:** `DIRECT_PDF` is produced only for a verified freely reachable PDF descriptor;
  tests cover dead URLs, redirects, HTML responses, restricted sources, timeouts, and alternate
  candidate fallback.

### API-002 — `open_access=false` applies the opposite of the client meaning

- **Priority:** P0
- **Status:** Open
- **Owner:** Server
- **Current behavior:** Any non-null `open_access` value is forwarded to OpenAlex as `is_oa:<value>`.
  Android always sends `false` when the “open access only” toggle is disabled.
- **Impact:** Default Android searches are restricted to `is_oa:false`, excluding open-access
  papers instead of leaving results unrestricted.
- **Planned resolution:** Treat only `open_access=true` as an active restriction. `false` and an
  omitted value both mean no open-access filter.
- **Exit criteria:** A cross-contract test proves that the default Android request does not add an
  `is_oa:false` filter, while `open_access=true` adds `is_oa:true`.

### API-003 — `topic` is interpreted as an OpenAlex topic ID

- **Priority:** P1
- **Status:** Open; business decision recorded
- **Owner:** Server
- **Current behavior:** `topic=education` becomes the OpenAlex filter `topics.id:education`.
- **Required behavior:** `topic` is a case-insensitive free-text topic constraint.
- **Planned resolution:** Translate free text using supported OpenAlex search/filter semantics or
  a server-side topic-resolution step. If exact IDs are needed later, introduce a distinct
  parameter such as `topic_id`; do not overload `topic`.
- **Exit criteria:** Contract tests use ordinary text such as `education` and demonstrate relevant
  filtering without requiring a `T...` identifier.

### API-004 — Not-found and upstream failure behavior conflicts with the agreed policy

- **Priority:** P1
- **Status:** Blocked by shared rollout
- **Owner:** Server, coordinated with Android
- **Current behavior:** Detail, refresh, and download may encode not-found as `200` with null
  envelope fields. Refresh encodes an upstream failure as `200` plus `access.state=ERROR`;
  download can encode an upstream failure as `200` plus a null descriptor. Search/details use a
  generic `502` for both timeout and other upstream failures.
- **Required behavior:** Follow recorded decision 3.
- **Planned resolution:** Return `404` for an unknown resource, distinguish `502` from `504`, and
  reserve `200 + descriptor:null` for an existing paper that currently has no confirmed PDF.
- **Exit criteria:** All four endpoints have integration tests covering success, empty search,
  unknown paper, no-PDF, upstream timeout, and upstream unavailable without leaking an upstream
  response body.

### API-005 — PDF-only filtering can produce unusable empty cursor pages

- **Priority:** P1
- **Status:** Open; business decision recorded
- **Owner:** Server
- **Current behavior:** The server filters one OpenAlex page locally and returns OpenAlex's cursor
  unchanged. The page may contain zero items even when later pages contain PDFs.
- **Impact:** The current Android UI treats an empty first page as terminal and cannot reach those
  later matches.
- **Planned resolution:** Aggregate bounded OpenAlex pages when `pdf_available=true` until the
  server has a useful response page, reaches the upstream end, or reaches a configured safety
  bound. The returned cursor must resume after the last upstream page inspected.
- **Exit criteria:** Tests cover PDFs on a later upstream page, no PDFs at all, safety-bound
  exhaustion, stable cursor continuation, and no duplicate returned work IDs.
- **Configuration still required:** Choose and document the maximum upstream pages/requests and
  overall time budget per client request.

### API-006 — Work type output is not normalized to the mobile contract

- **Priority:** P1
- **Status:** Open
- **Owner:** Server
- **Current behavior:** Raw OpenAlex `type` is returned. `book-chapter` therefore reaches Android,
  whose canonical contract value is `book_chapter`.
- **Impact:** Android maps book chapters to `PaperType.OTHER`.
- **Planned resolution:** Map every supported OpenAlex type to the canonical public values:
  `article`, `review`, `preprint`, `book_chapter`, and `dataset`; map other known values according
  to an explicitly documented fallback policy.
- **Exit criteria:** Request and response contract tests cover every supported type, especially
  `book_chapter`.

### API-007 — Invalid access URLs discard valid weaker candidates

- **Priority:** P1
- **Status:** Open
- **Owner:** Server
- **Current behavior:** If either access URL or descriptor is invalid, validation drops directly
  to `NO_FREE_FULL_TEXT`. The validator no longer has the alternate OpenAlex locations needed to
  perform the downgrade cascade described by the server design.
- **Impact:** An invalid PDF candidate can hide a valid open-HTML candidate.
- **Planned resolution:** Resolve and validate candidates in a structure that retains weaker
  alternatives, selecting the strongest valid access result.
- **Exit criteria:** A work with an invalid PDF URL and valid open-full-text URL returns
  `OPEN_FULL_TEXT`; a work with no valid candidate returns a non-actionable state.

### API-008 — Missing OA metadata is treated as negative evidence

- **Priority:** P1
- **Status:** Open
- **Owner:** Server
- **Current behavior:** Any value other than `is_oa=true`, including missing `is_oa`, follows the
  non-open branch and can become `PAYWALLED` or `NO_FREE_FULL_TEXT`. `oa_status` is modeled but not
  used.
- **Impact:** Unknown or incomplete OpenAlex data is represented with more certainty than the
  evidence permits.
- **Planned resolution:** Distinguish false from absent metadata and use `UNKNOWN` when the server
  cannot make a supported determination. Define how `oa_status` participates in resolution.
- **Exit criteria:** Tests independently cover `is_oa=true`, `false`, and null/inconsistent
  metadata.

### API-009 — `total_count` can be incorrect after local filtering

- **Priority:** P1
- **Status:** Open
- **Owner:** Server
- **Current behavior:** Search returns the upstream OpenAlex total even after local validation and
  PDF filtering remove items.
- **Impact:** The count does not necessarily describe the result set promised by the client query.
- **Planned resolution:** Return an accurate matched total when it can be computed without
  misleading the caller; otherwise omit it or return null. Never return an unfiltered total as the
  filtered total.
- **Exit criteria:** Total-count semantics are documented and tested for normal, validated, and
  PDF-filtered searches.

### API-010 — Blank and unsupported search values are not handled consistently

- **Priority:** P2
- **Status:** Open
- **Owner:** Server
- **Current behavior:** A present but blank `query` can reach OpenAlex. Unsupported `sort` and
  `work_type` values are silently ignored.
- **Impact:** Non-Android callers can receive broad or unexpectedly unfiltered results, and client
  mistakes are difficult to diagnose.
- **Planned resolution:** Validate non-blank queries and define one consistent policy for
  unsupported enum-like values: structured `400`, or an explicitly documented fallback.
- **Exit criteria:** Boundary tests cover blank query, invalid sort, invalid work type, and normal
  case-insensitive values.
- **Decision still required:** Whether unsupported `sort`/`work_type` values should be rejected or
  normalized to a fallback.

### API-011 — Stateless service retains unused database scaffolding

- **Priority:** P2
- **Status:** Open
- **Owner:** Server
- **Current behavior:** JPA, Data REST, MySQL, Docker Compose, and related compiler plugins remain
  configured although the service has no entities, repositories, or persistence requirement.
- **Impact:** Larger dependency surface, avoidable startup/deployment coupling, and confusion about
  whether server state is authoritative.
- **Planned resolution:** Remove unused persistence infrastructure while the service remains
  stateless, or document and implement a concrete cache/persistence requirement before retaining
  it.
- **Exit criteria:** Runtime dependencies match the implemented architecture and the service can
  start without an unrelated database requirement.

### API-012 — Maven model contains duplicate Lombok dependencies

- **Priority:** P2
- **Status:** Open
- **Owner:** Server
- **Current behavior:** `org.projectlombok:lombok` is declared three times and Maven warns that the
  effective model is malformed.
- **Impact:** Build noise and possible incompatibility with stricter future Maven versions.
- **Exit criteria:** Each dependency coordinate is declared once and `./mvnw test` emits no
  duplicate-dependency warning.

### API-013 — OpenAlex capacity and rate-limit strategy is undefined

- **Priority:** P2
- **Status:** Open; more important after pagination aggregation and PDF verification
- **Owner:** Server
- **Current behavior:** Every operation calls OpenAlex directly; there is no response cache,
  request coalescing, retry/backoff policy, or server-side rate limit.
- **Impact:** Increased latency and upstream exposure. Bounded page aggregation and future PDF
  verification will multiply outbound work.
- **Planned resolution:** Define request budgets, timeouts, backoff, observability, and an optional
  cache policy that does not change the public contract.
- **Exit criteria:** Operational limits and failure behavior are documented and load-tested.

### API-014 — Server documentation is stale and internally contradictory

- **Priority:** P2
- **Status:** Open
- **Owner:** Server
- **Current behavior:** `CLAUDE.md` still describes the repository as an empty scaffold, while the
  implementation and tests exist. The server design also differs from the Android contract on PDF
  verification, topics, and some errors.
- **Impact:** Future work can follow obsolete requirements and reintroduce contract drift.
- **Exit criteria:** Repository documentation reflects the implemented system and references this
  register plus the shared canonical contract.

## Android technical debt

### AND-001 — The application lacks Internet permission

- **Priority:** P0
- **Status:** Resolved (2026-09-10)
- **Owner:** Android
- **Resolution:** `src/integration/AndroidManifest.xml` declares `android.permission.INTERNET`; the
  `demo` flavor deliberately does not.
- **Evidence:** `DemoManifestTest`/`IntegrationManifestTest` assert both against the real merged
  manifests. `LiveBackendConnectivityTest` **ran on a device**
  (`./gradlew :app:atdIntegrationDebugAndroidTest`, Pixel 6 API 33 `aosp-atd`): a search reached a
  local HTTP server over the documented loopback host and a validated PDF transfer completed, while
  a non-PDF body was rejected even on a `200`. The demo suite passes with no Internet permission at
  all (22/22 in `atdDemoDebugAndroidTest`).

### AND-002 — No deliberate live-backend build is configured

- **Priority:** P0 for integration; intentional for demo builds
- **Status:** Resolved (2026-09-09)
- **Owner:** Android/build configuration
- **Resolution:** `demo` and `integration` product flavors hardcode `FAKE`/`HTTP` in BuildConfig, so
  the mode is a property of the build rather than something inferred from whether configuration
  happens to be complete. The backend URL is the only external input; a missing, malformed, or
  cleartext-in-release URL fails assembly with an actionable message and never substitutes fake data
  or the simulated download gateway. No OpenAlex credential is present in either APK.
- **Evidence:** `BackendConfigurationTest`, `DemoBackendSelectionTest` /
  `IntegrationBackendSelectionTest`, per-flavor
  `DemoFlavorConfigurationTest`/`IntegrationFlavorConfigurationTest`;
  `./gradlew :app:verifyBothFlavors -PpaperReaderBackendBaseUrl=<url>` assembles and tests both
  modes and runs `checkNoOpenAlexSecret` plus `checkNoOpenAlexSecretInArtifacts`.

### AND-003 — Local HTTP development is not explicitly supported

- **Priority:** P2
- **Status:** Resolved (2026-09-10)
- **Owner:** Android
- **Resolution:** `src/integrationDebug/res/xml/network_security_config.xml` permits cleartext only
  to `10.0.2.2`, `localhost` and `127.0.0.1`, and lives in the `integrationDebug` source set alone.
  Configuration validation rejects a cleartext URL for any other host and rejects cleartext outright
  in a release build. README documents the emulator and `adb reverse` routes.
- **Evidence:** `LiveBackendConnectivityTest` **ran on a device** and reached a cleartext loopback
  server, proving the debug route works. `IntegrationManifestTest` and
  `./gradlew :app:verifyIntegrationReleaseNetworkPolicy` prove the release build carries no
  exception: "Internet permission present, no cleartext exception."

### AND-004 — Empty cursor pages are treated as terminal

- **Priority:** P1
- **Status:** Resolved (2026-09-09)
- **Owner:** Android, defensive counterpart to API-005
- **Resolution:** `SearchCursorSession` owns one query's traversal below Compose. It remembers every
  cursor it has followed, stops on a repeated or non-advancing cursor with a typed pagination
  contract failure, traverses at most three consecutive empty pages automatically, and publishes one
  logical outcome per action so a later non-empty page never appears after a terminal empty state.
  Bound exhaustion retains the cursor and renders `SearchContent.MorePagesPending`, which offers
  continuation rather than claiming there are no matches.
- **Evidence:** `SearchCursorSessionTest` (empty-first→non-empty, empty-append→non-empty, terminal
  empty, bound exhaustion, repeated cursor, self-pointing cursor, failed continuation retaining its
  cursor, overlap-only page) and `SearchViewModelTest` (no terminal empty state is ever observed
  before the later page arrives).
- **Note:** this is a client-side bound, not a substitute for API-005's server-side aggregation.

### AND-005 — Not-found is not a first-class domain outcome

- **Priority:** P1
- **Status:** Resolved (2026-09-09)
- **Owner:** Android
- **Current behavior:** `RepositoryError` has first-class `NotFound` (non-recoverable),
  `ServiceUnavailable` (recoverable), `InvalidRequest` (non-recoverable) and `NoDownloadableContent`
  outcomes. `HttpPaperRemoteDataSource` maps 400 → `InvalidRequest`, 404 → `NotFound`,
  502 and 503 → `ServiceUnavailable`, 504 → `Timeout`, and keeps every other status
  without reading its response body. A `200` that cannot be mapped stays a `Contract` failure, and a
  detail or descriptor response about a different paper is a distinct contract failure. Paper detail
  renders a stable, terminal `PaperDetailContent.NotFound` that suppresses any cached copy and its
  access actions.
- **Evidence:** `HttpPaperRemoteDataSourceTest` (real HTTP statuses), `PaperOutcomeMapperTest`,
  `PaperRepositoryImplTest`, `PaperDetailViewModelTest`, `RemoteModeParityTest`.
- **Resolution:** the blocker was that the server had to actually return these statuses (API-004).
  It now does. `RealBackendSmokeTest` verifies against a running server that a syntactically valid
  unknown ID is `404 → NotFound` and that an existing paper with no free PDF is
  `200 + descriptor:null → DownloadAvailability.Unavailable`, not an error. `CanonicalContractTest`
  drives the provider's own published 400/404/502/503/504 Problem Details through the production
  status policy. The mapping is no longer exercised only against consumer-authored fixtures.
- **Exit criteria:** Details and download flows can present a stable not-found outcome;
  502/503/504 remain recoverable service failures; malformed success bodies remain contract failures.
  **Met.** API-004 itself remains the provider's item to close in its own register.

### AND-006 — Downloads do not re-confirm the descriptor with the server

- **Priority:** P1
- **Status:** Resolved (2026-09-09)
- **Owner:** Android
- **Resolution:** `PaperRepository.confirmDownloadAvailability` calls
  `GET /v1/papers/{paperId}/download` and maps it to `DownloadAvailability.Available` /
  `Unavailable` / a typed failure, validating response identity, HTTPS, and expiry against the
  injected clock. `DownloadsRepositoryImpl` calls it immediately before **every** initial enqueue and
  retry and passes the fresh descriptor into the gateway; `DownloadGateway.retry` now takes that
  descriptor and `WorkManagerDownloadGateway` replaces the stored URL and MIME type before scheduling
  work. A cached descriptor never authorizes a transfer on its own. Confirmation failures surface as
  accessible, dismissible feedback on search, detail, and Downloads instead of being discarded.
- **Evidence:** `DownloadsRepositoryImplTest` (fresh-descriptor success plus every non-enqueueing
  outcome, and an assertion that the cached URL is never a fallback),
  `WorkManagerDownloadGatewayTest` (URL/MIME replacement, one work chain per paper),
  `FakeDownloadManagerTest`, `PaperOutcomeMapperTest`, `SearchViewModelTest`,
  `PaperDetailViewModelTest`.

### AND-007 — Search totals are parsed but discarded

- **Priority:** P1 product correctness
- **Status:** Resolved (2026-09-09)
- **Owner:** Android
- **Resolution:** `total_count` flows through `CursorPage.totalItems`, the existing
  `search_queries.totalCount` column, `observeSearch`, and `SearchContent.Loaded.totalItems`. A first
  page states the total including stating its absence (clearing a previously cached one); an append
  updates it only when the response actually carried one. A negative total is treated as absent.
  Nothing is ever derived from loaded rows, page size, or page count, and the copy distinguishes
  "Showing N" from "Showing N of M".
- **Evidence:** `PaperOutcomeMapperTest`, `PaperRepositoryImplTest`, `SearchViewModelTest`,
  `PaperReaderApiContractTest`, and the re-recorded search screenshot golden.
- **Note:** `differentiate-screen-states` owns the final count presentation and must consume these
  types rather than reintroducing its own.

### AND-008 — Work type parsing is not tolerant of upstream/canonical variants

- **Priority:** P2 after API-006
- **Status:** Resolved (2026-09-09)
- **Owner:** Android
- **Resolution:** Each supported `PaperType` carries an explicit canonical `wireValue`, and requests
  emit only those — never text derived from the enum name. Parsing normalizes case and treats
  hyphen and underscore alike, so `book-chapter`, `BOOK-CHAPTER` and `Book_Chapter` all resolve;
  `journal_article` remains accepted. Blank/null stays `UNKNOWN`, unrecognized stays `OTHER`, and the
  paper is never lost. `OTHER`/`UNKNOWN` have no wire value and can never be sent as a filter.
- **Evidence:** `PaperOutcomeMapperTest`, `RequestMapperTest`, and
  `PaperReaderApiContractTest.everySupportedLegacyAndUnknownWorkTypeResolves` against the pinned
  `search-work-types.json` fixture.

### AND-009 — Cursor append does not enforce paper-ID deduplication

- **Priority:** P1
- **Status:** Resolved (2026-09-09)
- **Owner:** Android
- **Resolution:** Room schema version 3 adds a unique index over `(queryKey, paperId)`.
  `SearchCacheDao.persistPage` is now the single transactional write path: it deduplicates within the
  incoming page, excludes IDs already associated with the query, preserves first-seen backend order,
  and allocates new positions after the current **maximum** position rather than the row count, so
  migrated gaps cannot reorder appends. `MIGRATION_2_3` removes existing duplicate associations by
  keeping the lowest position before creating the index, and touches nothing else.
- **Evidence:** `SearchCacheDaoTest` (duplicates within a page, cross-page overlap, overlap-only
  append, order after restart, repeated appends, position gaps, per-query isolation),
  `Migration2To3Test` (seeded duplicates collapse to their lowest position; papers, saved state,
  access snapshots and download records survive unchanged), `SearchViewModelTest`, and the exported
  `app/schemas/.../3.json`.

### AND-010 — Publication-year and work-type filters are not exposed in the UI

- **Priority:** P2
- **Status:** Resolved (2026-09-10)
- **Owner:** Android
- **Current behavior:** `SearchFilterControls` adds an exact publication-year field and a work-type
  chip group limited to the five canonical public values. Both route through the existing
  `PaperFilters` state, so a change starts a new first-page cursor session and both values survive
  sorting and pagination; each has an individual clear action and "clear all" remains exhaustive.
  Year validation uses one shared rule (`PublicationYear`), the same range response mapping accepts,
  and an out-of-range entry is explained rather than requested.
- **Evidence:** `SearchViewModelTest` (apply, clear-one, clear-all, sort and pagination
  preservation, invalid year), `RequestMapperTest` (canonical `book_chapter` emission and cache keys
  that distinguish year/type combinations), and `SearchFilterControlsTest`, which **ran on a device**
  — all eight cases pass, covering selected-state semantics, individual clearing, an explained
  out-of-range year, and both controls remaining operable at 320dp with a 2.0x font scale.

### AND-011 — Access-refresh endpoint is not part of a production user flow

- **Priority:** P2
- **Status:** Resolved (2026-09-09) on the Android side
- **Owner:** Android/product
- **Decision:** No product purpose was found for a second refresh path, and inventing a UI purely to
  create a call site risked conflicting with the established silent-access requirements. The client
  operation was removed instead.
- **Resolution:** `refreshAccess` is gone from `PaperReaderApi`, `PaperRemoteDataSource`,
  `HttpPaperRemoteDataSource`, `FakePaperRemoteDataSource`, `PaperRepository`, the
  `AccessRefreshRequestDto`/`AccessRefreshResponseDto` DTOs, their mapping code, and every test
  double. Full detail refresh remains the metadata/access refresh path, cached actionable states
  still expire conservatively, and transfer-time download confirmation (AND-006) supplies freshness
  where it actually matters.
- **Remaining scope:** the server may keep `/access:refresh`. Removing or repurposing it is a
  separate versioned change; reintroducing a client call requires an explicit product flow and a new
  capability delta rather than preserving dead code.

### AND-012 — Fake and HTTP modes do not have complete behavioral parity

- **Priority:** P2
- **Status:** Resolved (2026-09-09) for the Android half
- **Owner:** Android, validated jointly with Server
- **Resolution:** `FakeRemoteScenario` gives the fake source explicit `NOT_FOUND`, `NO_PDF`,
  `EMPTY_PAGES_WITH_CURSOR`, `UPSTREAM_UNAVAILABLE`, `UPSTREAM_TIMEOUT`, `MISMATCHED_IDENTITY` and
  `MALFORMED_DATA` cases, and an unknown paper now reports `NotFound` rather than a client-side
  contract failure. One scenario matrix runs against both sources.
- **Evidence:** `RemoteModeParityTest` (success, terminal empty, not-found, no-PDF, 502, 504,
  mismatched identity, malformed data — same repository-level outcome in both modes, with the
  transport difference confined below `PaperRemoteDataSource`) and `FakePaperRemoteDataSourceTest`.
- **Remaining scope:** provider-side fixtures for the same scenarios (see SHR-001).

### AND-013 — Android documentation is stale

- **Priority:** P2
- **Status:** Resolved (2026-09-09)
- **Owner:** Android
- **Resolution:** `CLAUDE.md` was rewritten from the verified state: it describes the app as
  implemented, names this register as authoritative, distinguishes `openspec/specs/` (shipped) from
  `openspec/changes/` (proposed), lists the active changes and their Room schema-version
  allocations, and carries the current flavored command set. `README.md` gained "Build modes",
  "Live-backend status" and an honest Limitations section including the accepted PDF-verification
  debt. `docs/architecture.md` and `docs/backend/*` now describe the flavors, the supported endpoint
  surface (and the deliberate absence of a client `access:refresh`), the status policy, and
  transfer-time confirmation. Two new documents were added:
  `docs/api-compatibility.md` (pinned contract version, v1 change rules, open gates) and
  `docs/android-debt-verification-matrix.md` (each register item mapped to its closing check).
  Links now target archived or main specs rather than deleted change paths.

## Shared technical debt

### SHR-001 — No cross-repository contract tests

- **Priority:** P0
- **Status:** Resolved (2026-09-09)
- **Owner:** Server and Android
- **Original evidence:** The API suite passed 70 tests and the Android JVM suite 119, but each
  tested its own assumptions. Android's HTTP tests used an in-process fake `PaperReaderApi`; API
  integration tests asserted only server-authored shapes. Both builds could stay green while
  request semantics and business outcomes disagreed.
- **Resolution:** One bundle, `paperreader-v1.0.0`, is now driven through both production stacks.
  The provider drives `contracts/v1/fixtures/canonical-fixtures.json` through Spring in
  `CanonicalProviderContractTest` and validates its own OpenAPI document and digests in
  `ContractAssetsTest`. Android vendors that bundle byte-exactly at
  `app/src/test/resources/contract/canonical`, re-hashes every file against the provider's published
  `SHA256SUMS`, and drives all twelve fixtures through the production Retrofit interface,
  `RemoteJson` converter, status policy, and mappers in `CanonicalContractTest` — asserting domain
  outcomes, not JSON field presence. Every published fixture must be consumed or explicitly waived,
  so a new canonical case cannot be silently ignored.
- **Evidence (2026-09-09):** provider `./mvnw test` — 126 tests, 0 failures, including
  `CanonicalProviderContractTest` (5) and `ContractAssetsTest` (4). Android
  `./gradlew :app:verifyBothFlavors` — 529 JVM tests, 0 failures, including `CanonicalContractTest`
  (14). `./gradlew :app:atdIntegrationDebugAndroidTest` — 32 instrumentation tests, 0 failures,
  0 skipped, including seven against a real server.
- **Exit criteria:** CI fails when either side changes a path, query meaning, canonical enum value,
  nullability rule, error status, pagination invariant, or access-state requirement incompatibly.
  **Met** for the pinned version. A *new* provider release still has to be re-vendored deliberately;
  the digest check fails on a mismatched copy but nothing yet notifies Android that a release
  happened. That notification path is release-engineering work tracked under SHR-003.

### SHR-002 — The contract has multiple competing sources of truth

- **Priority:** P1
- **Status:** Resolved (2026-09-09)
- **Owner:** Server and Android
- **Original behavior:** Retrofit DTOs, Android documentation, Android OpenSpec artifacts, server
  documentation, server OpenSpec artifacts, and implementation tests each described parts of the
  contract. They disagreed on PDF confirmation, topic semantics, and error handling.
- **Resolution:** `paperreader-v1.0.0` is the single authoritative definition: an OpenAPI 3.1
  document for the wire shapes plus `semantics.md` for the behavior JSON Schema cannot express
  (filter meaning, candidate precedence, access normalization, totals truthfulness, failure policy).
  It is vendored unmodified at `app/src/test/resources/contract/canonical`. The Android
  `contract/v1` bundle is demoted from a competing schema to what it now declares itself to be —
  the consumer-side edge cases the canonical fixtures do not enumerate (expired and malformed
  descriptors, mismatched identity, duplicate IDs, empty pages that still carry a cursor, unknown
  response fields) — and its error bodies were replaced with the canonical RFC 7807 Problem Details
  so the two can no longer disagree about error handling. `manifest.json` records the derivation.
- **Exit criteria:** Every operation, field, enum, error response, and semantic invariant has one
  authoritative definition and an explicit compatibility/versioning policy. **Met** — definition in
  the canonical bundle, policy in `docs/api-compatibility.md`.

### SHR-003 — Coordinated deployment/versioning policy is undefined

- **Priority:** P2
- **Status:** Open
- **Owner:** Server and Android/release management
- **Current behavior:** Contract changes assume synchronized source changes, but mobile clients can
  remain installed while the server advances.
- **Impact:** A correct server rollout can still break older installed applications.
- **Planned resolution:** Define additive-change rules, deprecation windows, supported client/API
  versions, minimum-client enforcement if ever needed, and rollback compatibility.
- **Exit criteria:** The server can deploy independently without breaking supported installed
  clients, and breaking changes require a new API version.

## Known deliberate design choices that are not debt

Unless the product direction changes, the following are intentional:

- The product is account-free and has no authentication or cloud profile.
- Android never contains an OpenAlex credential and never calls OpenAlex directly.
- The API returns external PDF descriptors rather than fetching or re-hosting PDF bytes.
- Android validates actionable URLs and fails closed on malformed access responses.
- Unknown future access-state values map to a non-actionable `UNKNOWN` state.
- Room is the Android UI's local source of truth.
- Fake mode remains available as an explicitly disclosed demo mode.
- The API remains stateless unless a concrete caching/persistence requirement is approved.

## Verification baseline

### 2026-09-08 (before the Android remediation)

- `PaperReaderApi`: `./mvnw test` — 70 tests passed.
- `PaperReader`: `./gradlew :app:testDebugUnitTest` — build successful; 119 recorded JVM tests
  passed.
- Android instrumented/device tests and a real end-to-end Android-to-API run were not part of this
  baseline.

### 2026-09-10 (instrumentation)

- `./gradlew :app:atdDemoDebugAndroidTest` — **22/22 passed**.
- `./gradlew :app:atdIntegrationDebugAndroidTest -PpaperReaderBackendBaseUrl=<url>` —
  **25/25 passed**, including the loopback search and the validated PDF transfer.
- Both ran on a build-managed Pixel 6 / API 33 `aosp-atd` (arm64) device, in about 30s each after
  the first image download. This closes AND-001, AND-003 and AND-010.
- Two defects were found only by running these: the `demo` flavor's `.demo` applicationId suffix
  broke `ExampleInstrumentedTest`, and `app/src/androidTestIntegration/` had gone stale against the
  `mapDownloadDescriptorResponse` → `mapDownloadAvailability` rename because only the demo
  androidTest source set had been compiled.

### 2026-09-09 (after the Android remediation)

- `PaperReader`: `./gradlew :app:verifyBothFlavors -PpaperReaderBackendBaseUrl=<url>` — successful.
  This assembles and tests both the `demo` and `integration` flavors, runs Android lint for each,
  runs the pinned v1 consumer-contract and fake/HTTP parity suites, and runs both OpenAlex secret
  scans over sources and packaged artifacts.
- `./gradlew :app:assembleIntegrationRelease :app:verifyIntegrationReleaseNetworkPolicy` — successful:
  Internet permission present, no cleartext exception in the merged manifest or packaged resources.
- **Superseded:** this run recorded the provider suite as *not run*, because the PaperReaderApi
  repository was not available from this environment. See the 2026-09-09 (live integration) entry
  below, where it was.

### 2026-09-09 (live integration and provider-backed contract closure)

The first run in which the Android client and a real PaperReader server spoke to each other, and the
first in which both repositories verified the *same* contract version.

- **Provider:** `./mvnw test` — **126 tests, 0 failures**, including `CanonicalProviderContractTest`
  (5) and `ContractAssetsTest` (4), driving `paperreader-v1.0.0` through Spring.
- **Android JVM:** `./gradlew :app:verifyBothFlavors -PpaperReaderBackendBaseUrl=http://10.0.2.2:8080`
  — **529 tests, 0 failures**, both flavors assembled, lint clean for each, both OpenAlex secret
  scans clean. Includes `CanonicalContractTest` (14), which re-hashes the vendored provider bundle
  against its published `SHA256SUMS` and drives all twelve published fixtures through the production
  Retrofit stack.
- **Android instrumentation:** `./gradlew :app:atdIntegrationDebugAndroidTest` — **32 tests, 0
  failures, 0 skipped** on a build-managed Pixel 6 / API 33 `aosp-atd` (arm64) device. Seven of them
  are `RealBackendSmokeTest`, which opened a socket to a real server at `10.0.2.2:8080` and verified
  search, cursor advancement without overlap, detail identity, `DIRECT_PDF` descriptor confirmation
  over HTTPS, the `200 + descriptor:null` no-PDF outcome, `404 → NotFound`, and a `pdf_available`
  scan. None skipped, so the backend was genuinely reached.
- **Provider gate command:** `./gradlew :app:testIntegrationDebugUnitTest --tests
  com.example.paperReader.contract.ContractBundleIntegrityTest` — **passes**. The provider's
  `CONSUMER-GATE.md` recorded this as *blocked, fails while compiling existing Android tests* on
  2026-09-09; that record is stale and should be updated on the provider side.
- **Closed by this run:** AND-005 (the server now returns the statuses the mapping was written
  against), SHR-001, SHR-002.
- **Still open:** SHR-003 (release management owns the deprecation window and the re-vendor
  notification path), and every `API-*` item, which are the provider's to close in its own register.
- **Observed risk, not fixed:** the `pdf_available=true` live case took **5.2s**. The server bounds
  that scan at 8s and Retrofit is left on its default **10s** read timeout, because
  `BackendModule.providePaperReaderApi()` never calls `.client(...)`. The margin is thin by
  construction and will close under load.

Green local suites do not close any item in this register unless that item's exit criteria and
cross-contract behavior are explicitly covered.
