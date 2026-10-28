# Android debt remediation — baseline, sequencing, and verification matrix

Companion to `TECHNICAL_DEBT.md` and the `remediate-android-technical-debt` OpenSpec change. It
records the pre-change baseline, how this change is sequenced against the other active proposals,
the Room schema-version allocation, and the exact evidence required before a register item is
closed.

## 1. Baseline (recorded 2026-09-08, before any remediation code landed)

| Check | Command | Result |
|---|---|---|
| JVM unit tests | `./gradlew :app:testDebugUnitTest` | BUILD SUCCESSFUL, 119 test cases |
| Android lint | `./gradlew :app:lint` | BUILD SUCCESSFUL, no reported issues |
| Debug assembly | `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL, `app-debug.apk` produced |
| Room schema | `app/schemas/.../2.json` | version 2 exported; `search_queries.totalCount` column already present; `search_results` has non-unique indices on `paperId` and `queryKey` only |

A green baseline closes nothing. Every item below stays open until its own evidence row is
satisfied — see the register's "Verification baseline" note.

## 2. Overlapping active changes and merge order

All five other changes were still at 0/N tasks when this change started, so nothing had landed that
this change could inherit. The agreed order is:

| Order | Change | Shared surface with this change | Handling |
|---|---|---|---|
| 1 | `remediate-android-technical-debt` (this) | `SearchViewModel`/`SearchUiState`, `SearchScreen`, `CursorPage`, `SearchQueryEntity`, `SearchResultEntity`, `DownloadsRepository`, `DownloadGateway` | Lands first: it owns the data-layer contracts the UI changes build on. |
| 2 | `differentiate-screen-states` | Result-count copy, `CursorPage.totalItems`, cached total, `StateComponents` | Totals are implemented **here** (section 8) because that change had not landed. It must consume `CursorPage.totalItems` / `SearchContent.Loaded.totalItems` rather than re-adding them, and it owns the final count *copy* and filter-aware empty surface. |
| 3 | `restructure-search-screen-chrome` | `SearchScreen` control region | Rebase onto the year/type filter controls added in section 9 instead of re-deriving the filter row. |
| 4 | `refine-paper-presentation` | `PaperCard`, `PaperDetailScreen`, `AccessPresentation` | No data-layer overlap; rebase onto the not-found detail state added in section 4. |
| 5 | `add-library-collections` | Room (new tables) | Takes schema version 4. |
| 6 | `add-in-app-pdf-reader` | Room (reading-position table), `DownloadsScreen` | Takes schema version 5. |

`restructure-search-screen-chrome` only needs a Room version if it stores its first-run flag in
Room; if it does, it takes version 6.

## 3. Room schema-version allocation

| Version | Owner | Contents |
|---|---|---|
| 2 | shipped | `downloads.sourceUrl`, `downloads.mimeType` |
| **3** | **this change** | unique index `index_search_results_queryKey_paperId` over `(queryKey, paperId)`, with a forward migration that keeps the lowest-position association for existing duplicates |
| 4 | `add-library-collections` | collection + membership tables |
| 5 | `add-in-app-pdf-reader` | reading-position table |
| 6 | `restructure-search-screen-chrome` (only if Room-backed) | first-run acknowledgement |

`search_queries.totalCount` already exists at version 2, so section 8 needs no migration — it fills
a column that was exported but never written.

## 4. Verification matrix

"Android evidence" is what this repository can prove on its own. "External gate" is what must also
happen before the register item may be marked complete.

| Item | Android evidence | External gate |
|---|---|---|
| AND-001 Internet permission | `IntegrationManifestTest`/`DemoManifestTest` read the real merged manifests; `LiveBackendConnectivityTest` does a loopback search and a validated PDF transfer, run via `atdIntegrationDebugAndroidTest` | none — satisfied 2026-09-10 |
| AND-002 Deliberate live build | `demo`/`integration`/`production` product flavors; `BackendConfigurationTest` covers valid demo, valid HTTP, missing URL, invalid URL, HTTP-in-debug, HTTP-in-release; `ProductionFlavorConfigurationTest` pins the deployed address and proves no loopback value reached it; `./gradlew verifyAllFlavors` | none |
| AND-003 Local HTTP development | `integrationDebug` `network_security_config.xml` limited to the loopback hosts; `IntegrationManifestTest`, `ProductionManifestTest`, and `./gradlew :app:verifyIntegrationReleaseNetworkPolicy :app:verifyProductionDebugNetworkPolicy :app:verifyProductionReleaseNetworkPolicy` prove every other variant carries no exception; `LiveBackendConnectivityTest` proves the debug route works | none — satisfied 2026-09-10 |
| AND-004 Empty cursor pages | `SearchCursorSessionTest` + `SearchViewModelTest` for empty-first→non-empty, empty-append→non-empty, terminal empty, bound exhaustion, repeated cursor | API-005 remains server-owned; the client bound is defensive only |
| AND-005 First-class not-found | `RepositoryError.NotFound`/`ServiceUnavailable`/`InvalidRequest`; `HttpPaperRemoteDataSourceTest` 400/404/502/503/504; `PaperDetailViewModelTest` not-found state; `RealBackendSmokeTest` 404 and no-PDF against a real server | none — satisfied 2026-09-09 |
| AND-006 Download reconfirmation | `DownloadsRepositoryImplTest` initial + retry matrices; `WorkManagerDownloadGatewayTest` URL replacement | download-endpoint semantics on the server (API-004) |
| AND-007 Totals | `PaperOutcomeMapperTest`, `PaperRepositoryImplTest`, `SearchViewModelTest` totals present/absent/cleared/preserved | `differentiate-screen-states` owns the final copy; API-009 owns server truthfulness |
| AND-008 Work-type aliases | `PaperOutcomeMapperTest` canonical/alias/case/blank/unknown; `RequestMapperTest` canonical emission | API-006 for the server side |
| AND-009 Stable cached identity | `SearchCacheDaoTest` duplicates/overlap/order/restart; `Migration2To3Test` with seeded duplicates | none |
| AND-010 Year/type filters | `SearchViewModelTest` apply/clear-one/clear-all/invalid year, `RequestMapperTest` cache keys; `SearchFilterControlsTest` Compose semantics run via `atdDemoDebugAndroidTest` | none — satisfied 2026-09-10 |
| AND-011 Remove access refresh | `refreshAccess` absent from `PaperReaderApi`, `PaperRemoteDataSource`, `PaperRepository`, DTOs, and fakes; suite green | server may keep `/access:refresh`; its removal is a separate versioned change |
| AND-012 Fake/HTTP parity | `RemoteModeParityTest` runs one scenario matrix against both sources | provider fixtures for the same scenarios |
| AND-013 Documentation | `README.md`, `CLAUDE.md`, `docs/architecture.md`, `docs/backend/*`, `docs/api-compatibility.md` reconciled after behavior lands | none |
| SHR-001 Contract tests | provider bundle vendored at `contract/canonical` with its `SHA256SUMS`; `CanonicalContractTest` re-hashes it and drives all twelve published fixtures through the production Retrofit stack; plus `ContractBundleIntegrityTest`, `PaperReaderApiContractTest`, `AccessContractTest`, `HttpPaperRemoteDataSourceTest`; gate: `./gradlew :app:verifyApiContract` | satisfied 2026-09-09 — provider `./mvnw test` runs `CanonicalProviderContractTest` against the same `paperreader-v1.0.0` |
| SHR-002 One contract source | canonical OpenAPI + `semantics.md` vendored unmodified; `contract/v1` demoted to consumer edge cases and repointed at the canonical Problem Details; `manifest.json` records the derivation | satisfied 2026-09-09 — the provider published `paperreader-v1.0.0` |
| SHR-003 Versioning policy | `docs/api-compatibility.md` records additive rules and support assumptions | **release management must own the deprecation window, and the re-vendor notification path** |

## 4b. How instrumentation is run

A **build-managed device** declared in `app/build.gradle.kts` (`testOptions.managedDevices`), not a
hand-made AVD: Gradle downloads the image, boots it, runs the tests, and tears it down.

| | |
|---|---|
| Device | Pixel 6, API 33, `systemImageSource = "aosp-atd"` |
| Why ATD | no Play Store, GMS or SystemUI — the background services that cause instrumentation flakiness — and a much lighter image than `google_apis_playstore` |
| Apple Silicon | the `arm64-v8a` variant is selected automatically; runs natively, no Rosetta |
| Commands | `:app:atdDemoDebugAndroidTest`, `:app:atdIntegrationDebugAndroidTest -PpaperReaderBackendBaseUrl=<url>`, or `:app:verifyConnected` for both |
| Isolation | its AVD lives in `~/.android/avd/gradle-managed/` and never touches a developer's own AVDs |

A hand-made AVD remains the better tool for the *manual* walkthrough, where a Play-Store image is
closer to production than an ATD image is.

## 5. Results of the 2026-09-09 verification run

| Check | Result |
|---|---|
| `./gradlew :app:verifyBothFlavors -PpaperReaderBackendBaseUrl=<url>` | **passed** — both flavors assembled, 248 demo / 249 integration JVM tests, lint clean for each, both OpenAlex secret scans clean |
| `./gradlew :app:verifyAllFlavors` (2026-09-12) | **passed** — all three flavors assembled and tested with no `-P` supplied, lint clean for each, both OpenAlex secret scans clean |
| `./gradlew :app:assembleIntegrationRelease :app:verifyIntegrationReleaseNetworkPolicy` | **passed** — "Internet permission present, no cleartext exception" |
| Production address cannot be redirected (2026-09-12) | **passed** — with `paperReaderBackendBaseUrl=http://10.0.2.2:8080` set machine-globally, `productionDebug` BuildConfig still carries `https://barretolab.tech/` while `integrationDebug` carries the loopback address; `ProductionFlavorConfigurationTest` asserts the same from inside the build |
| Production release signing (2026-09-12) | **passed** — `assembleProductionRelease` fails with `validateProductionReleaseSigning` naming the four missing properties; every other release variant still assembles |
| Misconfigured integration builds | **passed** — missing URL, unparseable URL, cleartext to a non-loopback host in debug, and cleartext in release each fail assembly with their own message; a loopback debug URL builds |
| Room migration 2→3 with seeded duplicates | **passed** — duplicates collapse to their lowest position; papers, saved state, access snapshots and downloads survive |
| Connected instrumentation, production (2026-09-12) | **passed** — `atdProductionDebugAndroidTest` 29/29, **0 skipped**, on a build-managed Pixel 6 / API 33 `aosp-atd` (arm64) device. The zero-skip count is the evidence: `RealBackendSmokeTest` skips when no server answers, so a full run proves the deployed backend at `https://barretolab.tech/` served live search, cursor advancement, detail identity, an HTTPS `DIRECT_PDF` descriptor, the no-PDF outcome, and `404 → NotFound`. |
| Connected instrumentation | **passed (2026-09-10)** — `atdDemoDebugAndroidTest` 22/22 and `atdIntegrationDebugAndroidTest` 25/25 on a build-managed Pixel 6 / API 33 `aosp-atd` (arm64) device, ~30s each. Closes AND-001, AND-003 and AND-010. Two defects surfaced only here: the `.demo` applicationId suffix broke `ExampleInstrumentedTest`, and `src/androidTestIntegration/` had gone stale against the `mapDownloadAvailability` rename. |
| Provider suite against the same contract version | **passed (2026-09-09)** — 126 provider tests, 0 failures, including `CanonicalProviderContractTest` and `ContractAssetsTest` against `paperreader-v1.0.0`. Closes SHR-001 and SHR-002. (This row read "not run" until the provider published and became reachable.) |
| Real backend, end to end | **passed (2026-09-09)** — `RealBackendSmokeTest`, 7/7 against a server on `10.0.2.2:8080`, none skipped. First run in which the client and a real server spoke to each other; `LiveBackendConnectivityTest` had only ever used an in-process `MockWebServer`. |
| Manual on-device walkthrough | **partially done** — the build-configuration half was verified from the command line (above) and the filter/accessibility half is now covered by instrumentation. The remaining human-judgement items (empty-page continuation, overlapping pagination, total copy, download reconfirmation feedback as a user sees it) still want a hand-driven pass; start any AVD from Android Studio's Device Manager and run the demo walkthrough in README. |
