## Why

The demo implementations ship inside the integration APK. `FakePaperRemoteDataSource`,
`FakeDownloadManager`, and `PaperFixtures` live in `app/src/main/`, so they compile into both
flavors, and R8 cannot strip them because `BackendModule.providePaperRemoteDataSource` and
`DownloadModule.provideDownloadGateway` reference both implementations at runtime for Hilt's benefit.

The result is a production binary carrying a fake data source and a download manager that fabricates
placeholder files. It is inert today — `PAPER_READER_REMOTE_MODE` is hardcoded `"http"` for the
integration flavor, so the fake branch can never be taken — but "unreachable" is a weaker guarantee
than "absent", and it rests on a runtime conditional rather than on the compiler.

Now is the moment because the live backend is verified and the app is being run against it for real.
The demo path is no longer the primary way anyone experiences the app, so the cost of separating the
two is at its lowest.

## What Changes

- Move `FakePaperRemoteDataSource` (with `FakeRemoteScenario`), `FakeDownloadManager`, and
  `PaperFixtures` from `src/main/` to the `src/demo/` source set.
- **BREAKING (internal wiring only):** replace the runtime `when (configuration.mode)` selection with
  compile-time Hilt binding. `BackendModule` in `src/main/` keeps only `provideBackendConfiguration`;
  a new `DemoBackendModule` binds the fakes and a new `IntegrationBackendModule` binds
  `HttpPaperRemoteDataSource`, `WorkManagerDownloadGateway`, and `providePaperReaderApi`. No UI,
  domain, or repository code changes.
- Move the seven fake-dependent test files from the shared `test` source set to `testDemo`.
- Replace `BackendSelectionTest` with per-flavor `DemoBackendSelectionTest` and
  `IntegrationBackendSelectionTest`; the latter asserts `Class.forName` fails for every fake class,
  which is a stronger guarantee than the runtime branch check it replaces.

`BackendMode` and `BackendConfiguration` are unchanged: the configured mode still drives the
demo-data banner and the URL validation that makes a misconfigured integration build fail loudly.

## Capabilities

### New Capabilities

_None._ This changes how an existing requirement is satisfied, not what the app must do.

### Modified Capabilities

- `backend-ready-data-layer`: "Replaceable remote source" currently permits selection "through
  dependency injection **or** build configuration", which a runtime mode switch satisfies. The
  requirement tightens: the unselected implementation must not be present in the build at all.

## Impact

**Code**
- Moved: `data/remote/FakePaperRemoteDataSource.kt`, `data/download/FakeDownloadManager.kt`,
  `data/remote/fixtures/PaperFixtures.kt` → `src/demo/`
- Rewritten: `di/BackendModule.kt`, `di/DownloadModule.kt`
- New: `src/demo/.../di/DemoBackendModule.kt`, `src/integration/.../di/IntegrationBackendModule.kt`
- KDoc fix: `data/download/ContentUriProvider.kt` referenced `FakeDownloadManager` by symbol

**Tests**
- Seven files move `test` → `testDemo`; `BackendSelectionTest` is replaced by two per-flavor tests.
- `testIntegrationDebugUnitTest` drops from 265 to 209. Of the 56 tests it loses, most concern the
  fakes themselves; the genuine reduction is 19 flavor-independent repository tests that now run
  under `demo` only. Integration keeps every suite covering its distinctive behavior:
  `HttpPaperRemoteDataSourceTest`, `CanonicalContractTest`, `PaperReaderApiContractTest`,
  `AccessContractTest`, `RealBackendSmokeTest`.

**Docs**
- `CLAUDE.md`, `README.md`, `docs/architecture.md`, `docs/backend/demo-mode.md` all describe the
  fakes as living in `main`; `TECHNICAL_DEBT.md` AND-002 cites `BackendSelectionTest` by name.

**Not affected**
- The APK's runtime behavior in either flavor, the Room schema, the contract bundles, and every
  live-backend suite.
