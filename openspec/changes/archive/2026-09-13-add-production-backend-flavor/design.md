## Context

`app/build.gradle.kts` today defines one flavor dimension, `environment`, with two flavors. `demo`
hardcodes `PAPER_READER_REMOTE_MODE = "fake"` and an empty URL; `integration` hardcodes `"http"` and
takes its URL from the `paperReaderBackendBaseUrl` Gradle property. Selection is compile-time: the
fakes live in `src/demo/` and are not on an integration build's classpath at all, and each flavor's
own Hilt module binds its implementations. `BackendConfiguration.resolve` is the single runtime
validation point, mirrored by `backendBaseUrlProblem` in the build script so a misconfiguration fails
at assembly rather than at first launch.

The backend is now deployed at `https://barretolab.tech/`. The Retrofit paths in `PaperReaderApi`
are relative (`v1/papers/search`, `v1/papers/{paperId}`, `v1/papers/{paperId}/download`), so the base
URL is the bare origin with a trailing slash — `BackendConfiguration.ensureTrailingSlash` already
normalizes that.

Three properties of the existing arrangement constrain the design:

1. `paperReaderBackendBaseUrl` is conventionally set in `~/.gradle/gradle.properties` (the README
   tells developers to do exactly this, because the Run button has nowhere to pass `-P`). On a
   developer machine it is almost always `http://10.0.2.2:8080`.
2. The `INTERNET` permission is declared only in `src/integration/AndroidManifest.xml`, never in
   `src/main` — a networked flavor that does not inherit it is silently offline.
3. The assembly-time URL check (`selector().withFlavor("environment" to "integration")`) and the
   merged-manifest network-policy check (`selector().withName("integrationRelease")`) are both keyed
   to literal flavor/variant names. A new flavor inherits neither.

## Goals / Non-Goals

**Goals:**

- A build target that unambiguously means "the deployed PaperReader service" and cannot be pointed
  anywhere else by configuration a developer forgot they had set.
- Identical HTTP wiring between `integration` and `production` — one Hilt module, one manifest, one
  set of classpath-absence and binding assertions — so the two cannot drift.
- Cleartext impossible for `production` in every build type, verified against the real merged
  manifest and packaged resources rather than by inspection.
- `integration` and `production` installable side by side, with `production` owning the shippable
  applicationId.
- A signed, installable production release artifact.

**Non-Goals:**

- Any change to product behavior, the v1 wire contract, Room, or the UI.
- A staging target. A third address would be a fourth flavor, not a property on this one.
- Generating or committing a keystore. The build consumes signing material; creating it is an
  operator step.
- Closing SHR-003 (the provider deprecation-window policy) or API-001 (server-side PDF
  verification). A production *build* existing does not make a production *release* advisable.
- De-duplicating the loopback host allowlist, which exists in three unsynchronized copies.

## Decisions

### 1. A third flavor, not a build type or a property

`production` joins the existing `environment` dimension. The alternative — a `production` build type
— was rejected because the repository's central invariant is that the backend mode is a property of
the *flavor*, with the implementation bound by a flavor source set; a build type cannot carry a
source set in that dimension and would reintroduce the runtime-branch shape that AND-002 removed.
The other alternative — keep two flavors and simply document
`-PpaperReaderBackendBaseUrl=https://barretolab.tech/` — was rejected because it leaves the
production address as something a developer types correctly each time, which is precisely the failure
mode that matters.

### 2. The production URL is a build constant with no override

`PRODUCTION_BACKEND_BASE_URL` is a `val` in the build script, written into the flavor's BuildConfig
directly. `production` does not read `paperReaderBackendBaseUrl`.

The rejected alternative was an override property (`paperReaderProductionBaseUrl`) for staging. It
was rejected on the grounds that every override path is a way to build an artifact labelled
"production" that is not production, and the value it buys — a staging target — is better served by a
flavor that says "staging" on the tin. The constant is also what makes the guarantee testable: a unit
test in `src/testProduction/` can assert the literal value, the HTTPS scheme, and that the host is
not in `LOOPBACK_DEVELOPMENT_HOSTS`, which would be meaningless if the value were configurable.

### 3. `integration` defaults to the deployed URL

`paperReaderBackendBaseUrl` resolves to `PRODUCTION_BACKEND_BASE_URL` when unset or blank, rather
than to `""`. A supplied value still wins, so the `10.0.2.2` local-server workflow is untouched.

This is a deliberate relaxation. AND-002 records "fails to assemble without a valid backend URL" as
an exit criterion, and that specific failure disappears. What AND-002 is actually protecting against —
an HTTP build quietly showing fixture data — is untouched, because the fakes are not on the
classpath and the fallback is a real backend, not a simulated one. The residual risk is a developer
who meant to hit a local server, forgot the property, and reaches the live service instead; that is
a visibly wrong dataset rather than a silently plausible one, and it is a strictly smaller failure
than the build error it replaces was preventing. AND-002's wording is updated rather than left to
contradict the code.

### 4. `src/http/` shared between the two HTTP flavors

AGP flavor source sets are convention-based but their `srcDirs` are additive, so both flavors can
include `src/http/java` and point `manifest.srcFile` at one shared `src/http/AndroidManifest.xml`.
`IntegrationBackendModule`/`IntegrationBackendBindsModule` are renamed to `HttpBackendModule`/
`HttpBackendBindsModule` and move there, as does the `INTERNET` declaration.

The alternative — duplicating the module and manifest into `src/production/` — was rejected because
two Hilt modules binding the same interfaces to the same implementations is exactly the kind of
near-identical pair that drifts, and the drift would be invisible until a production build behaved
differently from the integration build that was tested. The same reasoning extends to `src/testHttp/`
(the classpath-absence and binding assertions, which are byte-identical for both flavors) and
`src/androidTestHttp/` (`RealBackendSmokeTest`, whose `assumeTrue` skip-when-unreachable probe is
what makes it safe to share — against production it actually runs).

`src/integrationDebug/` stays exactly where it is. The cleartext loopback exception is the one thing
that must *not* be shared.

### 5. Checks generalize off the flavor name

The per-variant URL validation loops over the HTTP flavors instead of selecting one by name, with
`requireHttps = flavor == "production" || buildType == "release"` — production requires HTTPS in
debug too, since it has no loopback story. The network-policy verification becomes a helper
registering `verify<Variant>NetworkPolicy` over `integrationRelease`, `productionDebug` and
`productionRelease`, using `variant.name` for the intermediates path. Registering it for
`productionDebug` is the substantive addition: the current check is release-only because
`integrationDebug` is *expected* to carry a cleartext exception, but for production the debug variant
needs the same guarantee as the release one.

The existing task name `verifyIntegrationReleaseNetworkPolicy` falls out of the helper unchanged, so
documentation and habits keep working.

### 6. `production` takes the bare applicationId

`production` → `com.example.paperReader`; `integration` → `com.example.paperReader.integration` with
`-integration` in the version name; `demo` unchanged at `.demo`. The shippable build should carry the
shippable identity, and all three then coexist on a device with distinguishable launcher entries.

The cost is that an existing `integration` install does not upgrade in place — it becomes a second
app, and its Room database and downloads stay with the old id. This is acceptable because
`integration` has never been distributed to anyone; it is a development build on developer devices.

### 7. Signing is property-driven and fails closed for production only

`signingConfigs.release` reads four Gradle properties; the `release` build type uses it only when all
four resolve, so `demo`/`integration` release builds still assemble on a machine with no keystore.
`assembleProductionRelease` additionally depends on a validation task that fails with an actionable
message when they are missing, matching the URL check's fail-at-assembly philosophy: an unsigned
production release is not a useful artifact, whereas an unsigned integration release is a normal
intermediate.

## Risks / Trade-offs

- **A forgotten `-P` now silently reaches the live backend instead of failing the build** (decision
  3) → The reached backend is the real one, not fixture data, so the failure is visible in the
  results rather than plausible-looking. The README's "omitting the URL is not a soft failure"
  section is rewritten rather than left stale, and `verifyIntegration`'s description drops its
  "requires `-P`" clause so the change is discoverable from `./gradlew tasks`.
- **Sharing `src/http/` across flavors is a less common AGP arrangement** than one directory per
  flavor, and `srcDirs` misuse (`srcDirs(...)` replaces, `srcDir(...)` appends) silently drops the
  default `src/<flavor>/java` → The shared `HttpBackendSelectionTest` runs in both flavors' unit test
  suites and fails loudly if the bindings are not present, so a mis-wired source set cannot pass
  `verifyAllFlavors`.
- **Renaming `IntegrationBackendModule` touches a class named in `docs/architecture.md` and in the
  archived `isolate-demo-implementations` change** → Documentation is updated in this change;
  archived change directories are historical record and are deliberately left alone.
- **`production` release builds now depend on operator-held signing material** → The failure is an
  explicit message naming the four properties, at assembly time, not a silent unsigned APK.
- **Three flavors mean ~50% more variant tasks and a third lint/test run** → `verifyAllFlavors`
  keeps one entry point; `verifyBothFlavors` remains as an alias so existing invocations and CI
  snippets do not break.
- **Android Studio still defaults to `demoDebug`** (alphabetically first), so "production" is never
  what you get by accident → unchanged behavior, already documented in the README, and the demo
  build's placeholder banner makes it self-announcing.

## Migration Plan

1. Land the build and source-set changes; `verifyAllFlavors` is the gate.
2. Operator generates a release keystore and adds the four signing properties to
   `~/.gradle/gradle.properties`. Until then, `verifyProduction` (which assembles only the debug
   variant) passes and only `assembleProductionRelease` fails.
3. Existing `integration` installs are uninstalled or simply left alongside the new ones; no data
   migration is offered or needed.

Rollback is building `demo` or `integration` and withholding the production variant — unchanged from
the existing policy. Room schema is untouched by this change, so nothing to downgrade.

## Open Questions

- Whether `production` should eventually gain a distinct app name or icon so the three launcher
  entries are distinguishable at a glance. Out of scope here; the version-name suffixes make the
  distinction visible in settings.
- Whether the live `RealBackendSmokeTest` run against production belongs in a routine verification
  task or stays an explicit `atdProductionDebugAndroidTest` invocation. Kept explicit for now, since
  it puts real traffic on the deployment.
