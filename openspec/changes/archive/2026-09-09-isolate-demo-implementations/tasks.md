## 1. Move the demo implementations out of `main`

- [x] 1.1 Move `FakePaperRemoteDataSource.kt` (with `FakeRemoteScenario`) to `src/demo/java/com/example/paperReader/data/remote/`.
- [x] 1.2 Move `FakeDownloadManager.kt` to `src/demo/java/com/example/paperReader/data/download/`.
- [x] 1.3 Move `fixtures/PaperFixtures.kt` to `src/demo/java/com/example/paperReader/data/remote/fixtures/`.
- [x] 1.4 Fix the `ContentUriProvider` KDoc, which referenced `FakeDownloadManager` as a resolvable symbol.

## 2. Replace runtime selection with per-flavor binding

- [x] 2.1 Reduce `main`'s `BackendModule` to `provideBackendConfiguration`; remove `providePaperRemoteDataSource` and move `providePaperReaderApi` out.
- [x] 2.2 Remove `provideDownloadGateway` from `main`'s `DownloadModule`, leaving `WorkManager`, `OkHttpClient`, `@DownloadsDir`, and `DownloadFileStorage` in place.
- [x] 2.3 Add `src/demo/.../di/DemoBackendModule.kt` binding the fake source and the simulated gateway.
- [x] 2.4 Add `src/integration/.../di/IntegrationBackendModule.kt` binding the HTTP source and the WorkManager gateway, and owning `providePaperReaderApi`.
- [x] 2.5 Confirm `BackendMode` and `BackendConfiguration` are untouched, so the demo banner and the fail-loud URL validation still work.

## 3. Relocate and re-aim the tests

- [x] 3.1 Move the six fake-dependent suites plus `PaperFixturesTest` from `test` to `testDemo`.
- [x] 3.2 Delete `BackendSelectionTest`; its runtime-branch question no longer exists.
- [x] 3.3 Add `DemoBackendSelectionTest` asserting the demo module binds the fakes.
- [x] 3.4 Add `IntegrationBackendSelectionTest` asserting `Class.forName` fails for every fake class and for `DemoBackendModule`, and that the HTTP source and WorkManager gateway are what is bound.

## 4. Verify

- [x] 4.1 Both flavors compile and both JVM suites pass — 261 demo, 209 integration, 0 failures.
- [x] 4.2 Scan the packaged APKs: 0 fake references in integration, 96 in demo, `HttpPaperRemoteDataSource` still present in integration.
- [x] 4.3 `./gradlew :app:verifyBothFlavors` passes, including lint and both OpenAlex secret scans.
- [x] 4.4 Instrumentation passes on the managed device — 22/22 demo, 32/32 integration with the live backend reachable.
- [x] 4.5 Confirm `integrationRelease` still rejects a cleartext base URL and still accepts an HTTPS override.

## 5. Documentation

- [x] 5.1 Update `CLAUDE.md` for the new source-set layout and the compile-time binding.
- [x] 5.2 Update `docs/architecture.md`: package map, fake-mode section, and build-modes table.
- [x] 5.3 Update `README.md` build-modes table and the architecture-highlights bullet.
- [x] 5.4 Update `docs/backend/demo-mode.md`'s path to `FakePaperRemoteDataSource`.
- [x] 5.5 Repoint `TECHNICAL_DEBT.md` AND-002's evidence from `BackendSelectionTest` to the two per-flavor tests.
- [x] 5.6 Add a "Running from Android Studio" section to `README.md`: variant selection, the `paperReaderBackendBaseUrl` property, the release override, and the two-application-ID trap.
