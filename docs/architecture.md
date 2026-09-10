# Architecture

This document is the durable reference for how PaperReader's code is organized and how data moves
through it. `CLAUDE.md` covers the same ground for an AI coding agent's day-to-day use; this file
is the human/onboarding version and goes into more depth on Room ownership and the fake→real
backend swap. See the archived milestone design at
`openspec/changes/archive/2026-09-08-build-openalex-paper-reader/design.md`, and the design of any
active change under `openspec/changes/`, for *why* these decisions were made — including
alternatives that were rejected. `TECHNICAL_DEBT.md` records what is knowingly still missing.

## Package structure

Everything lives under `com.example.paperReader`, organized by layer first and by feature within
`ui/`:

```
domain/
  model/        Paper, AccessResolution, CursorPage<T>, DownloadRecord, filters/sort/query types —
                plain Kotlin, no Android or Room dependencies.
  repository/   PaperRepository, LibraryRepository, DownloadsRepository, DownloadGateway
                interfaces, plus RepositoryResult<T> and the RepositoryError taxonomy. These
                interfaces expose no transport or Room types.
  usecase/      Small, single-purpose operations (SearchPapersUseCase, ToggleSavedPaperUseCase)
                that ViewModels call instead of talking to repositories for multi-step logic.

data/
  remote/       DTOs (dto/PaperDtos.kt), PaperMappers (DTO -> domain, fail-closed access rules),
                RequestMappers (domain -> request DTO), RemoteJson (shared kotlinx.serialization
                config), PaperRemoteDataSource interface, and its two implementations:
                FakePaperRemoteDataSource (the `demo` flavor) and HttpPaperRemoteDataSource (the
                `integration` flavor). Both are real implementations; one scenario matrix asserts
                they produce identical repository-level outcomes.
  remote/fixtures/  Deterministic JSON fixtures the fake source reads, shaped like real DTOs.
  local/        Room entities/DAOs/database/migrations, LocalMappers (Room row -> domain, which
                re-validates access state on every read — see "Room as source of truth" below).
  repository/   Repository implementations coordinating a remote source with Room.
  download/     WorkManager-based production download worker/gateway plus FakeDownloadManager.

ui/
  search/, paperdetail/, library/, downloads/   One package per feature: Screen composable,
                UiState, ViewModel.
  components/   Shared composables reused across features: PaperCard, AccessActionRow (the single
                access-to-UI mapping point), LoadingState/EmptyState/RecoverableErrorState/
                StaleDataNotice/DemoDataNotice, AccessibleButton/AccessibleIconButton.
  navigation/   PaperReaderRoute (type-safe sealed routes) and PaperReaderApp (Scaffold + bottom
                NavigationBar + NavHost).
  theme/        Material 3 tokens derived from docs/ui/lattice-search-reference.png.

di/             Hilt modules: BackendModule (fake vs. HTTP remote source), DatabaseModule,
                RepositoryModule, DownloadModule, CoroutineModule.
core/           Cross-cutting utilities with no feature affiliation (Clock, BackendConfiguration).

test/resources/contract/v1/   The checksum-pinned consumer-contract bundle: request expectations,
                response/error fixtures, and a manifest recording the API version, provenance and
                per-file SHA-256. See docs/api-compatibility.md.
```

## MVVM data flow

```
Composable screen
   │  reads UiState via collectAsStateWithLifecycle(), calls ViewModel functions on user actions
   ▼
ViewModel (StateFlow<UiState>, immutable state, one-off effects via a separate Flow/Channel)
   │  calls use cases or repositories directly; never touches Retrofit, DAOs, or WorkManager
   ▼
Repository (PaperRepository / LibraryRepository / DownloadsRepository)
   │  coordinates a remote source with Room; decides cache policy, staleness, offline fallback
   ├──▶ PaperRemoteDataSource (Fake or Http) ──▶ DTOs ──▶ PaperMappers ──▶ domain models
   └──▶ Room (DAOs) ──observed as Flow──▶ LocalMappers ──▶ domain models
   ▼
UI state is always built from what Room returns, not directly from a remote response — see below.
```

Composables never call Retrofit, DAOs, or download APIs directly; only repositories and ViewModels
do. A remote or fake response is always mapped and persisted to Room *before* the UI sees it, so
online, offline, and test behavior all flow through one path.

## Room as the source of truth

Room owns two kinds of data with different lifecycles:

- **Cache data** (search results, cached paper metadata, access snapshots, recent-query history):
  expirable, replaced transactionally per query, and safe to prune when stale.
- **User-owned data** (saved papers, download records): never pruned by cache-cleanup logic, kept
  independently of whatever search produced them.

A `PaperRow` projection (paper + its latest access snapshot + saved flag) is the read path for
every screen (search, detail, library). Critically, `LocalMappers.toDomain` re-validates the
persisted access snapshot through the exact same `PaperMappers.mapAccessResolution` logic a fresh
remote response would use, and downgrades a `DIRECT_PDF`/`OPEN_FULL_TEXT` snapshot to `Unknown`
once it is older than `ACCESS_FRESHNESS_MILLIS` (24h). This means the fail-closed access rule (see
`specs/verified-paper-access/spec.md`) holds even for data that has been sitting in the database —
a stored access state is never trusted blindly. `LocalMappersTest` covers this directly.

Room's schema is versioned (`PaperReaderDatabase`) and exported to `app/schemas/` via KSP
(`room.schemaLocation`) so `MigrationTestHelper`-style tooling and code review can see exactly what
changed between versions. `Migration1To2Test` builds the true v1 `downloads` table by hand (the
migration it regression-tests exists because `DownloadEntity` gained columns without a version
bump) and asserts `MIGRATION_1_2` upgrades it in place without losing rows.

## Fake-mode behavior

See `docs/backend/demo-mode.md` for the full picture: `FakePaperRemoteDataSource` is the default
`PaperRemoteDataSource` binding, and it routes fixture JSON through the same DTOs, serializers,
mappers, and Room persistence a real HTTP response would use — never hard-coded UI models. The app
shows a `DemoDataNotice` banner whenever it's running in fake mode so this is never mistaken for a
live integration.

## Build modes

The backend mode is fixed by the product flavor, never inferred at runtime from whether
configuration happens to be complete. There is no `paperReaderRemoteMode` property any more.

| | `demo` | `integration` |
|---|---|---|
| `BackendMode` | `FAKE`, hardcoded in the flavor's BuildConfig | `HTTP`, hardcoded |
| Remote source | `FakePaperRemoteDataSource` | `HttpPaperRemoteDataSource` |
| Download gateway | `FakeDownloadManager` | `WorkManagerDownloadGateway` |
| Backend URL | not used | required: `-PpaperReaderBackendBaseUrl=<url>` |
| `INTERNET` permission | absent | declared in `src/integration/AndroidManifest.xml` |
| Cleartext | none | `integrationDebug` only, and only for `10.0.2.2`, `localhost`, `127.0.0.1` |

`BackendConfiguration.resolve` is the single validation point. A missing URL, an unparseable URL, a
cleartext URL in a release build, or cleartext to a host outside the loopback allowlist each
produce a typed `BackendConfigurationProblem` — the integration flavor never substitutes fake data
or a simulated download gateway. The same rule is mirrored in the build script so a misconfigured
integration APK fails at assembly rather than at first launch.

Rolling back a live integration means building `demo` and withholding the integration variant, not
downgrading the database: Room changes are forward-migrated and hold user-owned saved and download
data.

## Supported remote operations

`PaperReaderApi` exposes exactly three operations: search, paper detail, and the download
descriptor. There is deliberately **no** `access:refresh` client operation — it had no production
call site (`TECHNICAL_DEBT.md` AND-011), and full detail refresh plus transfer-time download
confirmation already cover every freshness need. The server may keep the endpoint; reintroducing a
client call requires a defined product flow and a capability delta.

## Transport and domain outcomes

The status policy (recorded business decision 3) is implemented in `HttpPaperRemoteDataSource` and
mirrored by `FakeRemoteScenario`, so both modes produce the same repository-level result:

| Response | Outcome | Recoverable |
|---|---|---|
| `200`, empty items, no cursor | terminal empty page, not an error | n/a |
| `200`, empty items, new cursor | traversal continues, bounded | n/a |
| `200`, matching `paper_id`, `descriptor: null` | `DownloadAvailability.Unavailable` | no |
| `200` that cannot be mapped | `Contract` — never not-found | no |
| `200` about a different paper | `Contract(INVALID_PAPER_ID)` | no |
| `404` | `NotFound` | no |
| `502` | `ServiceUnavailable` | yes |
| `504` | `Timeout` | yes |
| other | `Http(code)`, body never read | `>=500` |

## Download confirmation

Every initial *and* retried transfer calls `GET /v1/papers/{paperId}/download` immediately
beforehand and enqueues only from the freshly returned descriptor, after checking that the response
is about the requested paper, that the URL is valid HTTPS, and that any expiry is still in the
future against the injected `Clock`. A descriptor cached in a `Paper` or a `DownloadEntity` never
authorizes a transfer on its own.

A new user action additionally requires the paper's *current* access to still be a confirmed
`DirectPdf`, so a transfer can only start from an affordance the user was legitimately shown. A
retry does not: the record already exists and the endpoint is the freshness authority even when the
cached access snapshot has gone stale.

## Contract versioning

`app/src/test/resources/contract/v1/` pins the request expectations and response/error fixtures
this client codes against, with a manifest recording the API version, provenance, and a SHA-256 per
file. Consumer tests drive that bundle through a real local HTTP server using the production
Retrofit interface, serializer, and mappers. `docs/api-compatibility.md` records the additive-change
rules for v1 and the two gates (provider CI verification, and the deprecation-window policy) that
cannot be closed from this repository.
