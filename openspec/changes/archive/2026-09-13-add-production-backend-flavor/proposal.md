## Why

The PaperReader backend is now deployed and reachable at `https://barretolab.tech/`; a probe of
`GET /v1/papers/search?query=test&limit=1` returns a live, contract-shaped v1 payload. Until now the
only live-backend build was `integration`, which requires the developer to supply an address and is
shaped for a *local* server behind a cleartext loopback exception. There is no build that
unambiguously means "the deployed PaperReader service", and no safe way to produce one: the
`paperReaderBackendBaseUrl` property is commonly set machine-globally to `http://10.0.2.2:8080`, so
any flavor that reads it can silently ship pointing at a developer's laptop.

## What Changes

- Add a third product flavor, `production`, in the existing `environment` dimension. It carries the
  deployed base URL as a **hardcoded build constant** and takes no external input at all, so it
  cannot be aimed elsewhere by a property, an environment variable, or a stale
  `~/.gradle/gradle.properties` line.
- `production` never carries a network-security cleartext exception, in **either** build type, and
  its base URL is required to be HTTPS in debug as well as release — stricter than `integration`,
  whose debug variant deliberately permits cleartext to loopback hosts.
- **BREAKING (developer-facing):** `integration` moves to applicationId suffix `.integration` and
  version-name suffix `-integration`; `production` takes the bare `com.example.paperReader`. An
  existing `integration` install becomes a separate app rather than being upgraded in place.
- **BREAKING (developer-facing):** `integration` now **defaults** to the deployed base URL instead of
  failing to assemble when no URL is supplied. `-PpaperReaderBackendBaseUrl` still overrides it, so
  the local-server workflow is unchanged when the property is set; what disappears is the
  hard assembly failure on omission. The invariant that matters is preserved — an HTTP flavor still
  never falls back to fixture data.
- `integration` and `production` share their wiring through a new `src/http/` source set (the
  `INTERNET` manifest and the Hilt module, renamed from `IntegrationBackendModule` to
  `HttpBackendModule`), plus shared `src/testHttp/` and `src/androidTestHttp/` sets so the
  classpath-absence, binding, and live-smoke assertions run identically for both.
- The build script's assembly-time base-URL validation and merged-manifest network-policy check stop
  being keyed to the literal flavor name `"integration"` and apply to every HTTP flavor and variant,
  including `productionDebug` — which the current release-only check would not have covered.
- Add a real release `signingConfig` driven by Gradle properties, and fail
  `assembleProductionRelease` with an actionable message when those properties are absent.
- Add `verifyProduction` and `verifyAllFlavors` verification tasks; `verifyBothFlavors` survives as
  an alias.

## Capabilities

### New Capabilities

None. This change adds no product behavior; it adds a build target for behavior that already exists.

### Modified Capabilities

- `backend-ready-data-layer`: the "Replaceable remote source" requirement currently describes a
  two-way demo/integration selection. It gains a third, deployment-targeted build, and a new
  requirement stating that the production build's backend address is fixed at build time with no
  external input and no cleartext exception in any build type.

## Impact

- `app/build.gradle.kts` — flavor definitions, base-URL resolution, per-variant validation,
  network-policy verification, signing config, verification tasks.
- Source sets — `src/integration/` contents move to a shared `src/http/`;
  `src/androidTestIntegration/RealBackendSmokeTest.kt` moves to `src/androidTestHttp/`; new
  `src/testHttp/`, `src/testProduction/`, `src/androidTestProduction/`.
- `app/src/main/.../core/config/BackendConfiguration.kt` — one error message names "the integration
  build" and must cover any HTTP flavor. No behavior change to `resolve`.
- Tests — `IntegrationBackendSelectionTest` and `IntegrationManifestTest` split into shared and
  flavor-specific halves; new production flavor-configuration and manifest tests.
- Docs — `README.md`, `CLAUDE.md`, `docs/architecture.md`, `docs/backend/demo-mode.md`,
  `docs/android-debt-verification-matrix.md`, and `TECHNICAL_DEBT.md` AND-002/AND-003 all document a
  two-flavor world.
- No change to `src/main` production code paths, the v1 wire contract, Room, or the UI. The demo
  flavor is untouched.
