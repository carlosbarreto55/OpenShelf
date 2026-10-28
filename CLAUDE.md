# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

PaperReader is a working Android app. Search, paper detail, library, downloads, Room caching,
WorkManager downloads, the fail-closed access rendering, and both a fake and an HTTP remote source
are **implemented and tested**. The PaperReader server is now deployed at `https://barretolab.tech/`
and the `production` flavor is pinned to it, so a build that returns real bibliographic results and
real PDFs exists. A production *release* is still gated on the deprecation-window policy (SHR-003)
and server-side PDF verification (API-001) — see `TECHNICAL_DEBT.md`.

Three things to keep straight before changing anything:

- **`TECHNICAL_DEBT.md` is the authoritative register** of known gaps across both repositories,
  including the recorded business decisions (topic is free text; 404/502/504 semantics; the client
  re-confirms downloads) that the code implements. An item stays open until its exit criteria are
  met — a green local suite closes nothing.
- **`openspec/specs/` holds the main capability specs** (what the app must do).
  `openspec/changes/` holds work that is *proposed but not implemented*. Do not read a change
  proposal as a description of shipped behavior.
- **`docs/android-debt-verification-matrix.md`** maps each register item to the exact check that
  closes it, and names which ones cannot be closed from this repository at all.

## Keeping this file current

**At the end of every task, re-read this file and ask whether the work just finished made any part
of it wrong or incomplete. If it did, update it as part of that same task** — not as a follow-up
suggestion for the user to accept. A stale CLAUDE.md misleads every later session, so treat the
update as part of the deliverable rather than an optional extra.

Things that routinely go stale here: the project-status paragraph, the active-changes list, the
Gradle command list (task names carry a flavor, so a new flavor or verification task changes it),
the architecture file and package tour, and the "Rules to preserve" invariants. A new invariant that
future work must not break belongs in "Rules to preserve" the moment it is established.

If nothing in the file is affected, say so briefly and move on — don't invent edits to prove the
check happened.

## OpenSpec workflow

Spec-driven development via OpenSpec (`openspec/config.yaml`, schema `spec-driven`). Each change
directory contains `proposal.md` (why/what/impact), `design.md` (decisions and rejected
alternatives — read this first), `tasks.md` (the ordered checklist), and `specs/*/spec.md`
(`Requirement` + `Scenario` WHEN/THEN deltas).

Use the `openspec-*` skills (or `/opsx:*` commands) to propose, update, or archive changes — don't
hand-edit these files as plain notes. Check task boxes as work completes.

Active changes at the time of writing: `restructure-search-screen-chrome`,
`refine-paper-presentation`, `add-library-collections`, `add-in-app-pdf-reader`. The last two each
take a Room schema version (4 and 5).

`differentiate-screen-states`, `remediate-android-technical-debt`, `add-production-backend-flavor`
and `isolate-demo-implementations` are **archived** under `openspec/changes/archive/` and their
deltas are merged into `openspec/specs/` — the technical-debt remediation (Room schema version 3),
the production flavor, and the per-flavor DI split described below are shipped behavior, not
proposals. The `remediate-android-technical-debt` archive also created two main specs:
`openspec/specs/api-contract-compatibility/` (the pinned-bundle and parity rules behind
`verifyApiContract`) and `openspec/specs/live-backend-connectivity/` (flavor selection, the
cleartext allowlist, and the live-server suite). Two of its requirement deltas were **deliberately
not applied**, because the two later changes had already superseded them: `Replaceable remote
source` and `Production-shaped placeholder fixtures` keep the newer main-spec text. Read the main
specs, not that archive, for current behavior.

`differentiate-screen-states` is shipped too: cause-classified error presentation, skeleton loading,
the filter-aware empty state, truthful result counts, and the grouped Downloads list are current
behavior, described by the "Rules to preserve" invariants below. Its `Topic search` delta was
likewise **not applied** — the main spec's standalone `Truthful result counts` requirement, added
later, already covers it.

## Commands

**Every task name carries a flavor.** There is no `testDebugUnitTest` any more.

```bash
./gradlew :app:assembleDemoDebug                # demo APK (fake data, no network)
./gradlew :app:testDemoDebugUnitTest            # JVM unit + Robolectric + screenshot tests
./gradlew :app:lintDemoDebug                    # Android lint
./gradlew :app:verifyApiContract                # pinned v1 consumer contract + fake/HTTP parity
./gradlew :app:verifyDemo                       # assemble + test + lint, demo
./gradlew :app:recordRoborazziDemoDebug         # re-record screenshot goldens after a UI change

# Production: the deployed backend at https://barretolab.tech/. Takes no arguments and ignores
# -PpaperReaderBackendBaseUrl entirely — the address is a build constant, not an input.
./gradlew :app:assembleProductionDebug
./gradlew :app:verifyProduction

# Integration: the same deployed backend by default, redirected with the property.
./gradlew :app:assembleIntegrationDebug -PpaperReaderBackendBaseUrl=http://10.0.2.2:8080
./gradlew :app:verifyIntegration        -PpaperReaderBackendBaseUrl=http://10.0.2.2:8080

./gradlew :app:verifyAllFlavors                 # all three (verifyBothFlavors is an alias)
./gradlew :app:atdDemoDebugAndroidTest          # Compose/instrumentation on a managed emulator
./gradlew :app:verifyConnected                  # instrumentation, all three flavors
./gradlew checkNoOpenAlexSecret                 # repository-wide secret scan
```

Run a single JVM test class or method with `--tests`:

```bash
./gradlew :app:testDemoDebugUnitTest --tests "com.example.paperReader.data.remote.PaperMapperTest"
```

Instrumentation uses a **build-managed device** (`testOptions.managedDevices`, a Pixel 6 API 33
`aosp-atd` image) rather than a hand-made AVD, so runs are reproducible and CI-ready. ATD images
carry no Play Store, GMS or SystemUI — the background services that cause instrumentation flakiness.
On Apple Silicon the arm64 variant is chosen automatically. Its AVD lives under
`~/.android/avd/gradle-managed/` and never touches a developer's own AVDs.

Note the shared `androidTest` source set runs against **all three** flavors,
`app/src/androidTestHttp/` against `integration` and `production`, and
`app/src/androidTestIntegration/` against `integration` alone. When changing a mapper or repository
signature, compile all of them — `assembleDemoDebugAndroidTest` alone will not catch a break in the
HTTP-only source sets.

No formatter beyond Android Lint; Kotlin style follows `kotlin.code.style=official`.

## Architecture

**Package root:** `com.example.paperReader` (note the mixed-case `paperReader` segment — match it
exactly). minSdk 26, target/compile SDK 36.

**Pattern:** Compose UI → ViewModel (`StateFlow<UiState>`, immutable state) → use
cases/repositories → Room (local source of truth) + remote data source. Composables never call
Retrofit, DAOs, or download APIs directly.

- `domain/model/PaperModels.kt` — `PaperId`/`SearchQuery` (validating value classes), `Paper`,
  `AccessResolution`, `DownloadAvailability`, `CursorPage<T>` (with an optional `totalItems`),
  `DownloadRecord`, `PaperType` (each supported value carries its canonical `wireValue`).
- `domain/model/PublicationYear.kt` — the single year-validation rule, used by both response
  mapping and the filter control.
- `domain/repository/` — repository interfaces plus `RepositoryResult<T>` and the `RepositoryError`
  taxonomy: `Offline`, `Timeout`, `NotFound`, `NoDownloadableContent`, `ServiceUnavailable`,
  `Http`, `Contract`, `AccessValidation`, `Unexpected`. Each has a safe `userMessage` and
  `isRecoverable`. No transport or Room types leak through these interfaces.
- `domain/usecase/SearchCursorSession.kt` — one query's bounded cursor traversal. Deliberately
  below Compose; see "Cursor traversal" below.
- `data/remote/dto/PaperDtos.kt` — `@Serializable` DTOs shaped for the PaperReader backend, not raw
  OpenAlex payloads. Optional restrictions are nullable: **null means "no restriction", and an
  inactive restriction is omitted from the wire entirely**, never sent as `false`.
- `data/remote/PaperMappers.kt` — DTO → domain, and the reference implementation of the fail-closed
  access rules. Read it before adding an access state.
- `data/remote/HttpPaperRemoteDataSource.kt` — status policy lives here: 400 → `InvalidRequest`,
  404 → `NotFound`, 502 and 503 → `ServiceUnavailable`, 504 → `Timeout`, everything else keeps its
  code. Response bodies from error statuses are never read.
- `src/demo/.../data/remote/FakePaperRemoteDataSource.kt` — the demo source, plus
  `FakeRemoteScenario` for reproducing not-found, no-PDF, empty cursor pages, 502/504, mismatched
  identity, and malformed data as the *same* repository-level outcomes HTTP produces. It lives in
  the **`demo` source set**, not `main`, so no fake implementation exists in an HTTP build;
  `HttpBackendSelectionTest` asserts the classes cannot even be loaded in either HTTP flavor.
- `data/local/` — Room. `SearchCacheDao.persistPage` is the single transactional write path for a
  mapped page.
- `ui/` — one package per feature (`search`, `paperdetail`, `library`, `downloads`) plus shared
  `components/`, `navigation/`, `theme/`. `MaterialTheme.spacing` is the spacing token source; do
  not hardcode `dp`. Two shared mapping points live in `components/`:
  `AccessPresentation.kt` (access → affordance) and `ErrorPresentation.kt`
  (`RepositoryError` → headline, explanation, retry, secondary route). `ui/search/ActiveFilters.kt`
  is the single enumeration of which filters are currently narrowing a search.
  For Material 3 component, theming and layout guidance, use the vendored `material-3` project
  skill (`.claude/skills/material-3/`, from `hamen/material-3-skill` v1.1.1, MIT). It is generic
  advice: where it conflicts with this file (e.g. `MaterialTheme.spacing`, the shared mapping
  points, fail-closed access rendering), this file wins.
- `di/` — `BackendModule` (in `main`) provides only `BackendConfiguration`. **Which** remote source
  and download gateway the app gets is bound per flavor: `src/demo/.../di/DemoBackendModule.kt` for
  fixtures, and `src/http/.../di/HttpBackendModule.kt` — compiled into *both* `integration` and
  `production` — for a real server. There is no runtime mode switch to get wrong; picking the wrong
  implementation is a compile error, not a branch.

## Rules to preserve

- **Fail-closed access rendering.** An access action may only ever be shown for a validated
  `AccessResolution.DirectPdf` (valid HTTPS descriptor) or `OpenFullText` (valid HTTPS source URL).
  Every other state — `NoFreeFullText`, `Paywalled`, `Unknown`, `Checking`, `Error`, or a malformed
  descriptor — renders with **no** badge, source URL, lock icon, or action, not even a disabled
  one. This holds on search cards, detail, library, and downloads alike. The single mapping point is
  `ui/components/AccessPresentation.kt`'s `toAccessAction()`.
- **Failures are presented by cause, through one mapping point.** `SearchContent.Error` and
  `PaperDetailContent.Error` carry the `RepositoryError` itself, never a flattened message, and
  `ui/components/ErrorPresentation.kt`'s `toPresentation()` is the only place a cause becomes copy.
  Its `when` is exhaustive over the sealed taxonomy, so a new variant is a compile error rather than
  a silent fall-through to a generic message. A retry is offered **only** when `isRecoverable` is
  true, every cause additionally names an `ErrorRoute` that still works under that failure (offline
  → the Room-backed Library), and the explanation is always the error's own `userMessage` — never a
  status code, contract-failure name, payload, URL or stack trace.
- **The active-filter list has one source.** `activeFiltersOf(PaperFilters)` enumerates which
  filters are on; the chip row and the filter-aware empty state both read it, so a new filter is
  added there and nowhere else.
- **Downloads are grouped by required action.** `DownloadsViewModel` computes ordered sections
  (in progress → needs attention → on this device) rather than inheriting DAO order; a canceled
  record leaves the list instead of becoming a group, and an empty group is never emitted, so no
  heading can stand over nothing. "Clear completed" removes the record and its managed file
  together, through the gateway that owns both.
- **No OpenAlex key or direct OpenAlex call.** The client talks only to the PaperReader backend.
  Two Gradle checks enforce this on sources and packaged artifacts.
- **Transfer-time descriptor confirmation.** Every initial *and* retried download calls
  `GET /v1/papers/{id}/download` immediately beforehand and enqueues only from that fresh,
  ID-matched, unexpired, valid-HTTPS descriptor. A descriptor cached in a `Paper` or a download
  record never authorizes a transfer on its own. A new user action additionally still requires the
  paper's current access to be a confirmed `DirectPdf`; a retry does not, because the endpoint is
  the freshness authority.
- **Room is the source of truth for the UI.** Remote or fake results are mapped and persisted, then
  the UI observes Room — never the network response directly.
- **One cached result per `(queryKey, paperId)`.** A unique index enforces it; page persistence
  deduplicates by first occurrence and allocates positions after the current *maximum*, never after
  the row count (migrated rows can leave gaps).
- **Cursor traversal is bounded and lives below Compose.** `SearchCursorSession` remembers every
  cursor it has followed, stops on a repeat or a non-advancing cursor, traverses at most
  `MAX_AUTOMATIC_EMPTY_PAGES` consecutive empty pages, and distinguishes "no matches" (terminal)
  from "nothing yet, cursor retained" (`SearchContent.MorePagesPending`). Never drive pagination
  from recomposition.
- **Totals are never synthesized.** `CursorPage.totalItems` carries what the source supplied, or
  null. A first page states the total including its absence; an append only updates it when the
  response actually carried one. Copy distinguishes "showing N" from "showing N of M".
- **Canonical out, tolerant in.** Requests emit only canonical work-type values; parsing accepts
  documented legacy aliases (`book-chapter`, `journal_article`) in any letter case, maps unknown
  values to `OTHER` and blank to `UNKNOWN`, and never loses the paper.
- **Never fabricate downloaded content.** The demo download writes a plain-text disclaimer, never
  bytes that could pass for the requested PDF. A failed download states that no partial file was
  kept, which is true because bytes are only ever written after PDF validation succeeds; the
  failure text itself comes from `DownloadFailureReason`, shared by the code that writes
  `DownloadRecord.errorMessage` and the code that explains it.
- **There is no `access:refresh` client operation.** It had no production call site (AND-011);
  detail refresh and download confirmation cover every freshness need. Do not reintroduce it
  without a defined product flow and a capability delta.

## Contract testing

**Two bundles, and the distinction is load-bearing.**

`app/src/test/resources/contract/canonical/` is the **provider's** `paperreader-v1.0.0` bundle,
vendored byte-exactly: OpenAPI 3.1, `semantics.md`, canonical fixtures, `VERSION`, and the
provider's own `SHA256SUMS`. It is the authoritative definition of v1. **Never hand-edit it** — to
take a new provider release, re-vendor the whole directory from `PaperReaderApi/contracts/v1` and
let `CanonicalContractTest` re-verify the digests. That test also requires every published fixture
to be consumed or explicitly waived, so a new canonical case cannot be silently ignored.

`app/src/test/resources/contract/v1/` is this repository's **consumer** bundle, derived from the
canonical one. It covers the edge cases the twelve canonical fixtures do not enumerate — expired and
malformed descriptors, mismatched response identity, duplicate IDs, empty pages that still carry a
cursor, unknown response fields. Its error bodies are the canonical RFC 7807 Problem Details.

Both drive a real local HTTP server (`ContractServer`) through the production Retrofit interface,
`RemoteJson` converter, and mappers — an in-process API double would skip exactly the seams where
drift was found.

Editing a consumer fixture without regenerating the manifest fails `ContractBundleIntegrityTest`.
Regenerate deliberately, as part of a reviewed contract-version change:

```bash
python3 tools/update-contract-manifest.py app/src/test/resources/contract/v1
```

`RealBackendSmokeTest` (in `androidTestHttp`, so it runs under both HTTP flavors) is the only suite
that talks to an actual server; it skips when none is listening. `LiveBackendConnectivityTest` next to it deliberately does
not — it uses an in-process `MockWebServer` to prove the cleartext-loopback and PDF-validation paths
deterministically.

`docs/api-compatibility.md` records the pinned version, the additive-change rules for v1, and what
remains open (SHR-003).
