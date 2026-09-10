# Demo (fake) backend mode

The `demo` product flavor shows placeholder data. Everything it displays — search results, paper
metadata, access states, download descriptors — comes from `FakePaperRemoteDataSource`
(`app/src/demo/java/com/example/paperReader/data/remote/FakePaperRemoteDataSource.kt` — the `demo`
source set, so it is absent from an integration build), **not** a
live OpenAlex or PaperReader backend integration. The demo APK does not even declare the `INTERNET`
permission.

The `integration` flavor is the live path; see `README.md` "Build modes". It is a separate build,
not a runtime switch, and it fails to assemble without a valid backend URL rather than falling back
to what this document describes.

## How placeholder data flows through real code

The fake source does not hand hard-coded domain/UI objects to the rest of the app. It:

1. Loads a small catalog from JSON fixtures (`data/remote/fixtures/PaperFixtures.kt`) using the
   same `kotlinx.serialization` DTOs (`data/remote/dto/PaperDtos.kt`) and `RemoteJson` config a
   real HTTP response would use.
2. Applies query/filter/sort/cursor-pagination logic and a configurable simulated network delay
   before responding, mirroring what a backend round-trip would look like.
3. Returns the same `RepositoryResult<T>` / `RepositoryError` types (`data/remote/HttpPaperRemoteDataSource.kt`
   uses the identical `PaperRemoteDataSource` interface), so callers cannot tell which
   implementation answered a request without inspecting `BackendConfiguration`. `RemoteModeParityTest`
   asserts this for a matrix of success, empty, not-found, no-PDF, 502, 504, mismatched-identity and
   malformed-data cases: the transport mechanism differs, the repository-level outcome does not.
4. Everything then passes through `PaperMappers` for validation and fail-closed access mapping
   exactly as a live response would.

This keeps the fake-to-real backend swap a dependency-injection change (`di/BackendModule.kt`),
not a rewrite.

## Fixture coverage

`PaperFixtures.catalog` includes one entry for each access/metadata case the product needs to
demonstrate: a confirmed direct PDF, a confirmed open-HTML source, no free full text, paywalled,
an unrecognized/future access value (proving forward-compatible mapping to `Unknown`), a paper
missing every optional field, and a second open-HTML entry. At the default page size (3) this
spans three cursor pages, so pagination is exercised without any special-casing.

## Making placeholder behavior visible

- `BackendConfiguration.mode` (from `core/config/BackendConfiguration.kt`) is `FAKE` in the `demo`
  flavor and `HTTP` in `integration`. Both are hardcoded in the flavor's BuildConfig — no Gradle
  property or runtime condition can turn one into the other.
- Whenever the app runs in `FAKE` mode, `PaperReaderApp` shows a `DemoDataNotice` banner
  ("Demo data — no live backend connected yet.") above the navigation host so demo behavior is
  never mistaken for a working integration.
- `FakePaperRemoteDataSource` exposes `latencyMillis` (simulated network delay), `forcedFailure`
  (a `RepositoryError` returned on every call until cleared), and `scenario`
  (a `FakeRemoteScenario`) so loading, retry, offline, and error states can be demonstrated or
  tested deterministically.
- `FakeRemoteScenario` reproduces the canonical HTTP edge cases without a server: `NOT_FOUND`,
  `NO_PDF`, `EMPTY_PAGES_WITH_CURSOR`, `UPSTREAM_UNAVAILABLE` (502), `UPSTREAM_TIMEOUT` (504),
  `MISMATCHED_IDENTITY`, and `MALFORMED_DATA`. The mechanism differs from HTTP — a status code
  there, an injected outcome here — but everything above `PaperRemoteDataSource` must see the same
  thing, which `RemoteModeParityTest` asserts.

## Switching to the live backend

Build the other flavor. There is no property that turns the demo build into a live one:

```bash
./gradlew :app:assembleIntegrationDebug -PpaperReaderBackendBaseUrl=http://10.0.2.2:8080
```

No UI, ViewModel, or domain code changes are required — both remote sources implement the same
`PaperRemoteDataSource` interface and produce the same DTOs. See `README.md` "Build modes" for the
emulator/ADB networking details and the HTTPS-only release rule.
