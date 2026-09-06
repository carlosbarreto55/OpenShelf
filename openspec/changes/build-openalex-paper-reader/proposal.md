## Why

PaperReader should turn OpenAlex's broad scholarly metadata into a polished, recruiter-ready Android research experience: users can discover papers by topic, understand what is actually accessible, and keep useful work locally without creating an account. The backend does not exist yet, so the first client milestone must remain demonstrable with realistic placeholder data while establishing contracts that can later connect to the OpenAlex, access-verification, scraping, and PDF-generation services without rewriting the UI or domain layers.

## What Changes

- Replace the empty Android shell with a Jetpack Compose, Material 3 application based on MVVM, unidirectional state, repositories, and dependency injection.
- Add topic-based paper discovery with debounced search, filter chips, sorting, loading/empty/error states, paper metadata cards, and paper details.
- Adopt `docs/ui/lattice-search-reference.png` as the initial visual direction for the search experience, including adaptive light/dark theming and accessible Compose semantics.
- Add verified access actions: show **Download PDF** only for a backend-confirmed PDF and **Read online** only for backend-confirmed open full text. Results whose free-full-text status is unavailable, paywalled, or inconclusive remain metadata-only and expose no access badge, source URL, lock, or button.
- Add Room-backed search caching, saved papers, recent queries, access snapshots, and download records so the app has useful local and offline behavior without accounts.
- Add a downloads experience with observable progress, retry/cancel states, local-file opening, and clear failure feedback.
- Define production-shaped remote DTOs, service interfaces, mappers, repositories, and dependency-injection bindings for a future PaperReader backend. Until that backend exists, select a deterministic fake remote data source with realistic paper and access states and simulate remote/download transitions.
- Keep the OpenAlex API key and any future scraping logic out of the Android client. The eventual backend will own OpenAlex calls, inspect all candidate locations, verify access, scrape only freely reachable content, and return or generate PDFs.
- Keep the app account-free: no authentication, registration, or cloud profile is introduced.

## Capabilities

### New Capabilities

- `paper-discovery`: Topic search, filtering, sorting, pagination-ready result presentation, paper metadata, paper details, and polished screen states.
- `verified-paper-access`: Conservative access-state rendering that exposes actions only for confirmed direct PDFs or confirmed open full text and suppresses all access UI otherwise.
- `offline-paper-library`: Room-backed result caching, recent searches, saved papers, and metadata availability across restarts and temporary connectivity loss.
- `paper-downloads`: PDF download lifecycle, progress and retry/cancel presentation, persisted download records, and opening completed local files.
- `backend-ready-data-layer`: Stable client-side service contracts, DTO mapping, repositories, and replaceable fake/real remote sources while the backend is unavailable.

### Modified Capabilities

None. This repository has no existing OpenSpec capabilities.

## Impact

- **Android code:** new Compose activity/navigation, feature screens, ViewModels, domain models/use cases, repository implementations, Room entities/DAOs/database, remote contracts, fake fixtures, and download abstractions under `app/src/main`.
- **Build:** add Kotlin, Compose, lifecycle, navigation, coroutines, serialization/networking, Room/KSP, dependency-injection, and test dependencies; retain minSdk 26 and target/compile SDK 36 unless implementation discovers an incompatibility.
- **Local data:** introduce a versioned Room schema for papers, queries/results, saved items, access snapshots, and downloads.
- **External systems:** no live backend is required for the first milestone. A future backend will provide normalized OpenAlex search/detail responses and verified access/download information; the client must never embed the supplied OpenAlex key.
- **Design asset:** `docs/ui/lattice-search-reference.png` becomes the implementation reference for the primary search screen.
