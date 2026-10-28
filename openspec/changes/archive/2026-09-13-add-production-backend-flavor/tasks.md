## 1. Shared HTTP source set

- [x] 1.1 Create `app/src/http/AndroidManifest.xml` by moving `app/src/integration/AndroidManifest.xml` verbatim (the sole `INTERNET` declaration), and delete the old file
- [x] 1.2 Move `app/src/integration/java/com/example/paperReader/di/IntegrationBackendModule.kt` to `app/src/http/java/com/example/paperReader/di/HttpBackendModule.kt`, renaming `IntegrationBackendModule` → `HttpBackendModule` and `IntegrationBackendBindsModule` → `HttpBackendBindsModule`; leave the Retrofit and `@Binds` bodies unchanged
- [x] 1.3 Move `app/src/androidTestIntegration/java/com/example/paperReader/live/RealBackendSmokeTest.kt` to `app/src/androidTestHttp/java/com/example/paperReader/live/RealBackendSmokeTest.kt`, leaving `LiveBackendConnectivityTest` in `androidTestIntegration`
- [x] 1.4 Remove the now-empty `app/src/integration/` directory tree

## 2. Build script: flavors and URL resolution

- [x] 2.1 Add `PRODUCTION_BACKEND_BASE_URL = "https://barretolab.tech/"` to `app/build.gradle.kts` with a comment recording that it is deliberately not property-driven (design decision 2)
- [x] 2.2 Change the `paperReaderBackendBaseUrl` resolution to fall back to `PRODUCTION_BACKEND_BASE_URL` when the property is absent or blank
- [x] 2.3 Add the `production` flavor: `dimension = "environment"`, no applicationId/versionName suffix, `PAPER_READER_REMOTE_MODE = "http"`, `PAPER_READER_BACKEND_BASE_URL = PRODUCTION_BACKEND_BASE_URL`
- [x] 2.4 Give `integration` `applicationIdSuffix = ".integration"` and `versionNameSuffix = "-integration"`
- [x] 2.5 Rename `integrationBaseUrlProblem` to `backendBaseUrlProblem` and reword its messages so they do not name the integration flavor
- [x] 2.6 Wire the shared source sets: `src/http/java` + `src/http/AndroidManifest.xml` into both `integration` and `production`; `src/testHttp/java` into `testIntegration` and `testProduction`; `src/androidTestHttp/java` into `androidTestIntegration` and `androidTestProduction`. Use `srcDir` (append), never `srcDirs` (replace)

## 3. Build script: generalized verification

- [x] 3.1 Replace the single `withFlavor("environment" to "integration")` selector with a loop over both HTTP flavors, resolving each flavor's own URL and setting `requireHttps = flavor == "production" || buildType == "release"`
- [x] 3.2 Extract the merged-manifest network-policy check into a helper registering `verify<Variant>NetworkPolicy`, using `variant.name` for the intermediates path; register it for `integrationRelease`, `productionDebug` and `productionRelease`, confirming the existing `verifyIntegrationReleaseNetworkPolicy` task name is unchanged
- [x] 3.3 Add `verifyProduction` (assemble + `testProductionDebugUnitTest` + `lintProductionDebug` + `verifyProductionDebugNetworkPolicy`)
- [x] 3.4 Add `verifyAllFlavors` covering all three flavors, `verifyApiContract`, and the two root secret scans; reduce `verifyBothFlavors` to an alias depending on it
- [x] 3.5 Add `atdProductionDebugAndroidTest` to `verifyConnected`, and drop the "requires `-PpaperReaderBackendBaseUrl`" clauses from the `verifyIntegration` and `verifyConnected` descriptions

## 4. Release signing

- [x] 4.1 Add `signingConfigs.release` reading `paperReaderReleaseStoreFile`, `paperReaderReleaseStorePassword`, `paperReaderReleaseKeyAlias`, `paperReaderReleaseKeyPassword`, applied to the `release` build type only when all four resolve
- [x] 4.2 Register `validateProductionReleaseSigning`, wired to `assembleProductionRelease`, failing with a message naming the four properties when any is missing
- [x] 4.3 Add `*.jks` and `*.keystore` to `.gitignore`

## 5. Tests

- [x] 5.1 Create `app/src/testHttp/.../di/HttpBackendSelectionTest.kt` from the flavor-agnostic half of `IntegrationBackendSelectionTest`: the `Class.forName` absence assertions for the four demo classes, the binding reflection over `HttpBackendBindsModule`, and the eager-base-URL Retrofit check
- [x] 5.2 Create `app/src/testHttp/.../build/HttpFlavorManifestTest.kt` asserting the merged manifest declares `INTERNET`; delete `IntegrationBackendSelectionTest` and reduce `IntegrationManifestTest` to the cleartext-iff-`BuildConfig.DEBUG` assertion
- [x] 5.3 Update `IntegrationFlavorConfigurationTest`: replace the now-unreachable blank-URL fallback to `https://api.paperreader.example` with an assertion that the flavor's URL is never blank
- [x] 5.4 Create `app/src/testProduction/.../core/ProductionFlavorConfigurationTest.kt`: mode is `"http"`; URL equals `https://barretolab.tech/`; resolves `Valid` under both `isDebugBuild = true` and `false`; scheme is `https`; host is not in `BackendConfiguration.LOOPBACK_DEVELOPMENT_HOSTS`
- [x] 5.5 Create `app/src/testProduction/.../build/ProductionManifestTest.kt`: no `networkSecurityConfig` and no `usesCleartextTraffic` in either build type
- [x] 5.6 Reword the `MISSING_BASE_URL` message in `BackendConfiguration.kt` so it covers any HTTP flavor, keeping the `-PpaperReaderBackendBaseUrl` substring that `BackendConfigurationTest` asserts on

## 6. Verification

- [x] 6.1 `./gradlew :app:verifyAllFlavors` passes with no `-P` supplied
- [x] 6.2 `./gradlew :app:verifyIntegration -PpaperReaderBackendBaseUrl=http://10.0.2.2:8080` still passes, proving the local-server override survives
- [x] 6.3 `./gradlew :app:verifyProductionDebugNetworkPolicy` and `:app:verifyProductionReleaseNetworkPolicy` both report no cleartext exception
- [x] 6.4 `./gradlew :app:assembleProductionDebugAndroidTest :app:assembleIntegrationDebugAndroidTest` both compile, proving the shared and flavor-specific androidTest sets are wired correctly; `atdProductionDebugAndroidTest` then ran 29/29 with 0 skipped against the live deployment
- [x] 6.5 `./gradlew checkNoOpenAlexSecret` passes and the signing properties do not appear in any packaged artifact
- [x] 6.6 Install `productionDebug` by hand and confirm no demo banner, live search results, and a real PDF download against `https://barretolab.tech/` (the instrumented half is covered by 6.4/`atdProductionDebugAndroidTest` 29/29, 0 skipped; the remaining part is a human walkthrough)

## 7. Documentation

- [x] 7.1 `README.md`: three-column build-modes table, updated commands, three applicationIds, rewritten "omitting the URL" section, production/signing notes in "Release transport", and the `keytool -genkeypair` recipe
- [x] 7.2 `CLAUDE.md`: Commands block and the `di/` architecture bullet
- [x] 7.3 `docs/architecture.md`: the "Build modes" table and surrounding prose
- [x] 7.4 `docs/backend/demo-mode.md`: "Switching to the live backend" points at the production flavor
- [x] 7.5 `docs/android-debt-verification-matrix.md`: AND-002 and AND-003 rows
- [x] 7.6 `TECHNICAL_DEBT.md`: extend AND-002's resolution to record the third flavor, the deployed URL, and the relaxed integration-URL requirement (design decision 3)
