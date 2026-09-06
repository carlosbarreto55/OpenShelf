## Context

PaperReader currently contains only a minimal Android application module: no activity, Kotlin source, Compose configuration, persistence, or application architecture exists. The target is an account-free Jetpack Compose research app that searches OpenAlex-derived paper metadata, renders verified access options conservatively, stores useful data with Room, and presents the visual language captured in `docs/ui/lattice-search-reference.png`.

The PaperReader backend is not implemented. That future service is expected to protect the OpenAlex API key, normalize OpenAlex's large payloads, inspect every candidate location, determine whether a direct PDF or open HTML source is genuinely reachable, and potentially scrape permitted open content into a generated PDF. The client milestone therefore needs convincing deterministic behavior without pretending that live resolution exists, while avoiding a disposable mock-only architecture.

Constraints include minSdk 26, no authentication or user accounts, no OpenAlex secret in the APK, offline-tolerant local features, and no access affordance when the backend would report no free full text or an inconclusive verification.

## Goals / Non-Goals

**Goals:**

- Establish a modular-by-feature Compose/MVVM foundation with unidirectional state and testable ViewModels.
- Deliver a polished search, detail, library, and downloads experience using realistic fixtures while the backend is absent.
- Make Room the local source of truth for cached metadata, recent searches, saved papers, access snapshots, and download state.
- Define stable domain models and production-shaped remote contracts so a real backend implementation can replace the fake through dependency injection.
- Make access rendering fail closed: only confirmed direct PDFs and confirmed open full text produce actions.
- Keep the UI responsive, accessible, previewable, and consistent with the supplied Lattice reference.

**Non-Goals:**

- Building, deploying, or selecting the technology stack for the backend.
- Calling OpenAlex directly from the production Android data flow or storing the OpenAlex API key in the app.
- Implementing publisher/repository scraping, paywall bypass, access verification, or PDF generation on-device.
- Authentication, accounts, cross-device sync, annotations, citation management, or AI paper summarization in this change.
- Treating placeholder links or simulated downloads as evidence that a paper is publicly available.

## Decisions

### 1. Use layered MVVM with Room as the local source of truth

The app will use Compose screens driven by immutable `UiState` values from ViewModels over `StateFlow`. ViewModels invoke small use cases or repositories; repositories coordinate remote sources and Room; composables do not call Retrofit, DAOs, or download APIs directly. Packages will be grouped by feature (`search`, `paperdetail`, `library`, `downloads`) with shared `domain`, `data`, and `core` packages where useful.

Room-backed flows are exposed to the UI after remote or fake data is mapped and persisted. This gives the same observable path online, offline, and under tests, and makes caching a real feature rather than a second code path.

Alternative considered: drive screens straight from a remote service and add Room only for bookmarks. Rejected because it duplicates state, weakens offline behavior, and makes fake-to-real backend replacement more invasive.

### 2. Put fake and future network implementations behind the same remote contract

Define a `PaperRemoteDataSource` (or equivalent gateway) with search, detail, access-refresh, and PDF-download-descriptor operations. A deterministic `FakePaperRemoteDataSource` will be the initial binding and return fixture DTOs representing confirmed PDF, confirmed open HTML, unavailable, and inconclusive access states. A non-active `HttpPaperRemoteDataSource` and Retrofit/Ktor service contract will be scaffolded against a configurable backend base URL; it must compile but need not be reachable.

Selection happens in the dependency-injection graph or build configuration, never inside ViewModels. Fake latency and failures are controllable so loading, retry, empty, and progress UI can be demonstrated and tested.

Alternative considered: hard-code sample domain objects in each ViewModel. Rejected because it does not validate serialization/mapping boundaries and would make the eventual backend integration a rewrite.

### 3. Model a small, normalized backend API instead of OpenAlex payloads

The client contract will describe normalized, pagination-ready operations similar to:

- `GET /v1/papers/search` with query, cursor, sort, year/type/topic/open-access/PDF filters.
- `GET /v1/papers/{openAlexId}` for normalized detail metadata.
- `POST /v1/papers/{openAlexId}/access:refresh` for an explicit recheck when supported.
- A backend-provided download descriptor or authenticated-free download endpoint for a confirmed PDF.

DTOs include only fields needed by the product: stable OpenAlex ID, title, authors, venue, year, type, topics, citation count, abstract when available, and a typed access resolution. The backend may eventually return either an original open PDF or a generated PDF from freely reachable scraped content; the Android client treats both as a confirmed PDF descriptor with provenance text.

The Android app will never send or embed the OpenAlex API key. The supplied key belongs in future backend configuration and must not be committed to this repository.

Alternative considered: mirror raw OpenAlex responses in the mobile app. Rejected because payloads are large and unstable for UI needs, access resolution requires examining multiple locations, and the key would be exposed.

### 4. Represent access as a fail-closed domain state

Use a sealed/enum domain model that can preserve backend distinctions such as `DIRECT_PDF`, `OPEN_FULL_TEXT`, `NO_FREE_FULL_TEXT`, `PAYWALLED`, `UNKNOWN`, `CHECKING`, and `ERROR`. UI mapping yields an action only for:

- `DIRECT_PDF` with a validated download descriptor: **Download PDF**.
- `OPEN_FULL_TEXT` with a validated HTTPS source URL: **Read online**.

Every other state yields no badge, source row, lock, URL, or action. A paper's bibliographic metadata remains visible. Invalid/missing URLs downgrade to no action instead of falling back to publisher landing pages.

Alternative considered: show locks or “access unknown” labels. Rejected because the product requirement is to keep those states visually silent and avoid confusing metadata availability with full-text availability.

### 5. Use Room tables that support both cached discovery and personal local state

The initial schema will include normalized paper metadata, paper authors/topics as embedded or related values, search queries and ordered search-result cross-references, saved-paper state, access snapshots with checked timestamps, and download records. User-owned records (saved and completed downloads) are retained independently from expirable search cache entries.

Repository transactions replace a query page atomically and keep stable paper IDs so the same paper can appear across searches without duplication. Cache freshness is recorded explicitly; stale data can remain visible with a refresh indicator rather than disappearing.

Alternative considered: a single JSON blob table. Rejected because it prevents useful observable queries, migrations, deduplication, and targeted updates to save/download state.

### 6. Keep paging and downloading behind abstractions

Search results will use a cursor-aware repository contract compatible with Paging 3. The first fixture set can be finite, but request/response models and Room ordering must not assume page numbers. This avoids coupling the app to OpenAlex cursor details while letting the future backend choose its pagination contract.

`PaperDownloadManager`/`PaperDownloadScheduler` will expose observable queued, running, completed, failed, and canceled states. The placeholder implementation simulates progress and writes no misleading paper content; a production implementation can later use WorkManager plus an HTTP client to stream a confirmed PDF to app-managed storage, validate MIME/signature, update Room, and open it through a `FileProvider`.

Alternative considered: call Android's system DownloadManager directly from composables. Rejected because it is difficult to fake deterministically, test, and reconcile with persistent domain state.

### 7. Treat the reference image as direction, not a rigid screenshot clone

The search screen will reproduce the Lattice hierarchy: compact brand header, editorial hero, prominent search field, horizontal filter chips, result count/sort, rich paper cards, selective access row, and Search/Library/Downloads navigation. Material 3 tokens, window insets, dynamic typography scaling, minimum touch targets, semantic labels, and dark/light palettes take precedence over pixel-perfect hard-coding. Compose previews will cover key access and screen states.

### 8. Separate failures by user-recoverable boundary

Search failures, stale-cache warnings, access-check failures, and download failures are distinct domain errors. A remote failure may fall back to cached results; a mapping/contract failure is logged without exposing secrets; an access-check failure never enables an action; and a download failure preserves a retryable record. ViewModels expose one-off effects only for navigation/open-file actions, while persistent feedback remains in state.

## Risks / Trade-offs

- [The fake backend drifts from the eventual service] → Version fixture JSON against the same DTO serializers, document endpoint assumptions, and add contract tests before enabling the HTTP binding.
- [Placeholder behavior is mistaken for a working OpenAlex integration] → Display a debug/demo data notice where appropriate and keep fake selection explicit in build configuration and README documentation.
- [Access state is stale or wrong] → Persist `checkedAt`, fail closed on missing/invalid descriptors, and require backend confirmation before rendering any access action.
- [Room schema becomes over-modeled too early] → Store only fields required by defined screens, keep migrations versioned, and avoid copying the entire OpenAlex graph.
- [Simulated downloads imply a real PDF exists] → Label demo behavior in debug builds and never create a fake document presented as the requested paper; production download remains disabled until a confirmed backend descriptor exists.
- [A generated PDF could omit publisher formatting or violate source terms] → Keep generation entirely backend-side, record provenance, restrict it to freely accessible content, and treat legal/source-policy review as a backend prerequisite.
- [Large result lists or recomposition harm performance] → Use lazy lists, stable keys, immutable UI models, paging-ready queries, and benchmark/trace the result card path once live data is available.

## Migration Plan

1. Add Kotlin/Compose and architectural dependencies, create the application/activity/theme/navigation shell, and keep the app runnable after each step.
2. Add domain models, normalized DTOs, fake fixtures, mappers, Room schema, and repository interfaces/implementations.
3. Implement search, detail, library, and download screens against the fake binding, following the UI reference and all fail-closed access rules.
4. Add tests for ViewModels, repository/cache behavior, Room migrations/DAOs, DTO mapping, and Compose semantics/screenshots.
5. When the backend exists, add its base URL through non-secret build configuration, run contract tests, and switch only the remote data-source binding for an integration build.
6. Roll back backend activation by restoring the fake binding; Room schema and UI/domain layers remain compatible. Do not roll back by placing an OpenAlex key in the client.

## Open Questions

- What will the backend's stable base URL, versioning policy, and cursor format be?
- Will a confirmed PDF response be a short-lived signed URL, a streamed endpoint, or both?
- How will the backend distinguish original PDFs from generated PDFs and expose provenance/license metadata?
- What cache TTL should apply to access verification versus bibliographic metadata?
- Should the production release hide the demo source entirely or retain it in a dedicated showcase build variant?
