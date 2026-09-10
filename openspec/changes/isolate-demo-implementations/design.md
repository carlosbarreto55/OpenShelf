## Context

`remediate-android-technical-debt` decision 1 established `demo` and `integration` as product
flavors so the backend mode could never be inferred at runtime. That held for the *mode*, but not
for the *code*: both implementations were left in `src/main/` and chosen by a runtime `when`, so
every APK carried both. `app/src/demo/` was created and never used.

The live backend is now verified end to end, so the demo path is no longer how the app is primarily
experienced, and separating the two costs less than it ever will again.

## Goals / Non-Goals

**Goals:**
- No fake implementation present in an integration build, provable by inspecting the packaged APK.
- Selection enforced by the compiler rather than by a runtime conditional.
- Keep the fakes and their deterministic failure injection fully available to the test suites.

**Non-Goals:**
- Deleting the fakes or the demo flavor. `FakeRemoteScenario` reproduces `NOT_FOUND`, `NO_PDF`,
  `EMPTY_PAGES_WITH_CURSOR`, `UPSTREAM_UNAVAILABLE`, `UPSTREAM_TIMEOUT`, `MISMATCHED_IDENTITY`, and
  `MALFORMED_DATA` — outcomes a healthy OpenAlex-backed server will not produce on request. Removing
  them would leave the fail-closed error handling untestable, and would mean a fresh clone could not
  run or test the repository layer without a Spring server on the machine.
- Changing runtime behavior of either flavor, the Room schema, or the contract bundles.
- Modularizing the app (see decision 3).

## Decisions

### 1. Bind per flavor; keep only configuration in `main`

`BackendModule` in `src/main/` provides `BackendConfiguration` and nothing else. `DemoBackendModule`
binds `FakePaperRemoteDataSource` and `FakeDownloadManager`; `IntegrationBackendModule` binds
`HttpPaperRemoteDataSource` and `WorkManagerDownloadGateway` and owns `providePaperReaderApi`, which
is the only place Retrofit is constructed and which the demo flavor therefore never compiles.

`BackendMode` and `BackendConfiguration` stay in `main` unchanged. The configured mode still drives
the `DemoDataNotice` banner and the base-URL validation that fails a misconfigured integration build
at assembly time. What is removed is only the *selection* use of that mode.

Alternative considered: keep the runtime switch and rely on R8 to strip the unused branch. Rejected —
both providers are referenced from a Hilt module, so both are reachable and neither is stripped. The
premise was false.

### 2. `HttpPaperRemoteDataSource` stays in `main`, the fakes do not

Asymmetric on purpose. The shared `test` source set drives `HttpPaperRemoteDataSourceTest`,
`CanonicalContractTest`, and `PaperReaderApiContractTest` under **both** flavors against a local
`MockWebServer`; moving the HTTP source into `src/integration/` would break those for `demo` while
buying nothing, since the class is what the app actually uses in production.

### 3. Accept the test-coverage asymmetry rather than duplicate the fake

Seven test files depend on the fakes and move to `testDemo`, so `testIntegrationDebugUnitTest` drops
from 265 to 209. Most of those tests are *about* the fakes and have no business running in the
integration flavor; the real reduction is 19 flavor-independent repository tests that now run under
`demo` only, which is ordinary practice.

Alternative considered: extract a `RecordingRemoteDataSource` test double into `src/test` so the
three repository suites stay shared. Cleaner symmetry, but it duplicates what
`FakePaperRemoteDataSource` already does well and means reworking 19 tests' setup. Worth revisiting
if the asymmetry starts to bite.

Alternative considered: a separate `:fake-backend` Gradle module consumed by both
`demoImplementation` and `testImplementation`. This is the textbook answer and would cost no coverage
at all, but the fake depends on the domain models and DTOs that live in `:app`, so it circular-depends
unless the domain layer is extracted into its own module first. Out of proportion to the problem.

### 4. Assert absence, not just selection

`BackendSelectionTest` proved the runtime `when` took the right branch. That question no longer
exists. `IntegrationBackendSelectionTest` instead asserts `Class.forName` throws
`ClassNotFoundException` for each fake class and for `DemoBackendModule` — testing the property this
change is actually for, and failing loudly if a fake is ever moved back into shared code. The
`@Binds` declarations are checked by reflecting on each flavor module's abstract methods.

## Risks / Trade-offs

- [Integration loses 19 repository tests] → They still run under `demo`, and the logic is
  flavor-independent. Integration retains every suite covering behavior unique to it, including
  `RealBackendSmokeTest` against a live server.
- [`testDemo` and `testIntegration` selection tests drift] → Each names the other in its KDoc, and
  both are small enough to read together. A binding change breaks compilation before it breaks a test.
- [Hilt duplicate-binding errors during the move] → Caught at compile time, not runtime; the failure
  mode is a verbose build error rather than a wrong implementation at runtime.
- [The demo flavor rots now that it is not the default experience] → `atdDemoDebugAndroidTest` and
  the demo JVM suite still run in `verifyBothFlavors`, and the screenshot goldens are demo-rendered,
  so a break surfaces in CI rather than at demo time.
