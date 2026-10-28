## Why

The Android client is production-shaped but cannot yet operate safely against the live PaperReader API: it lacks network/build wiring, disagrees with the agreed error and download semantics, and has pagination, identity, filtering, and contract-verification gaps. The authoritative `TECHNICAL_DEBT.md` register marks several of these as P0/P1, so the client side must be remediated before live integration is treated as valid.

## What Changes

- Add explicit demo and integration build configurations, Internet permission, and a narrowly scoped local-development network policy while keeping release traffic HTTPS-only (AND-001–003).
- Introduce first-class not-found handling; distinguish 404, 502, 504, malformed-success, no-PDF, offline, and timeout outcomes consistently across HTTP and fake sources (AND-005, AND-012).
- Re-confirm a paper's descriptor through the download endpoint immediately before every initial or retried transfer, validating response identity and descriptor content before enqueueing (AND-006).
- Make cursor consumption defensive: continue across bounded empty pages when a cursor remains, detect cursor loops, and deduplicate papers by stable ID transactionally without changing first-seen order (AND-004, AND-009).
- Carry optional matched totals through mapping, Room, repository, and UI without fabrication, coordinated with the existing `differentiate-screen-states` change (AND-007).
- Accept documented legacy work-type aliases defensively while continuing to emit and prefer canonical contract values (AND-008).
- Expose accessible publication-year and work-type filters, including clear/reset behavior and normalized cache identity (AND-010).
- Remove the unused Android access-refresh operation instead of introducing a second, undefined refresh UX; full detail refresh remains the metadata/access refresh path, and download confirmation becomes the transfer-time freshness path (AND-011).
- Add a versioned Android consumer-contract suite and compatibility policy for request semantics, response shapes, errors, cursors, totals, enum aliases, and access invariants; require parity fixtures for fake and HTTP modes (SHR-001–003, Android scope).
- Update Android documentation so implemented, proposed, demo-only, and blocked server-dependent behavior are clearly separated (AND-013).
- Do not implement server-owned API items or the accepted server-side PDF-verification debt; Android work that depends on the agreed server rollout remains guarded by consumer tests and feature/build configuration.

## Capabilities

### New Capabilities

- `live-backend-connectivity`: Deliberate demo/integration builds, Android network permission, local debug connectivity, HTTPS-only release behavior, and build-mode verification.
- `api-contract-compatibility`: Versioned Android consumer-contract coverage, fake/HTTP semantic parity, and a mobile-safe API compatibility and rollout policy.

### Modified Capabilities

- `backend-ready-data-layer`: Refine transport/domain outcomes, legacy enum tolerance, mode parity, and the supported remote operation surface.
- `paper-discovery`: Add defensive cursor advancement, truthful counts, stable-ID deduplication, accessible year/type filters, and a distinct paper-not-found presentation.
- `offline-paper-library`: Persist optional totals and enforce one ordered cached result per stable paper ID for each normalized query.
- `paper-downloads`: Require fresh download-endpoint confirmation before initial enqueue and retry, with explicit no-PDF, not-found, offline, timeout, mismatch, and malformed-response behavior.

## Impact

- **Build/runtime:** `app/build.gradle.kts`, manifests/source sets, BuildConfig values, Hilt bindings, debug network-security resources, and CI/local integration commands.
- **Domain/remote:** `RepositoryError`, cursor/total models, Retrofit request behavior, DTO mappers, `PaperRemoteDataSource`, and fake/HTTP implementations.
- **Persistence:** search-query/result entities and DAO transactions; a Room schema bump and migration may be required depending on which overlapping total/dedup work lands first.
- **Features:** search state/ViewModel/filter controls, detail not-found state, download repository/gateway contracts, and user-facing failure states.
- **Contract/tests/docs:** a versioned consumer fixture suite, HTTP/fake parity tests, migration/repository/ViewModel/Compose/instrumentation coverage, `README.md`, `CLAUDE.md`, and backend-facing Android documentation.
- **Sequencing:** land or incorporate `differentiate-screen-states` before the totals portion; rebase around other active UI/Room proposals, then coordinate 404/502/504 and canonical-contract activation with the server rollout.
