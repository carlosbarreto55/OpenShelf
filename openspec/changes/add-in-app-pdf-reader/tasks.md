## 1. Rendering source

- [x] 1.1 Add a `PdfPageSource` interface: open a managed file, report page count, render a page at a target size, close.
- [x] 1.2 Implement it with `android.graphics.pdf.PdfRenderer`, serialising every call on a single dedicated dispatcher so the one-page-at-a-time and thread-safety constraints are enforced in one place.
- [x] 1.3 Add open-time classification: not a PDF by file signature, encrypted, malformed, file missing, or readable.
- [x] 1.4 Add tests in `test` for a valid multi-page PDF, an encrypted PDF, a corrupt file and a missing file.
- [x] 1.5 Add a test in `testDemo` that classifies the actual file `FakeDownloadManager` produces as not a PDF — not a synthetic stand-in.
- [x] 1.6 Add a test that a page failing to render is reported as unavailable rather than returning a blank bitmap.

## 2. Reading position persistence

- [x] 2.1 Add a `reading_position` entity keyed by paper: page index, fractional intra-page offset, updated-at.
- [x] 2.2 Cascade removal so deleting a completed download and its file removes the stored position.
- [x] 2.3 Bump the Room schema to the next free version on `main` (3 today; `add-library-collections` may take 4 first), write the additive migration, and add a migration test from every exported earlier version asserting saved papers, collections if present, and download records survive.
- [x] 2.4 Add DAO operations: read a paper's position, upsert it, observe resumable papers ordered by most recently read.
- [x] 2.5 Add tests for upsert, cascade on download removal, and ordering of resumable papers.

## 3. Reader screen

- [ ] 3.1 Add `PaperReaderRoute.Reader(downloadId)` and wire it into the `NavHost` as a pushed destination.
- [ ] 3.2 Add the reader ViewModel: observe the download record and expose an immutable reader state per record state — queued/running, failed, canceled, completed.
- [ ] 3.3 While the record is queued or running, show determinate progress when byte counts are known and offer cancel; when it completes, proceed to file verification without user action.
- [ ] 3.4 When the record fails, present the cause through `ErrorPresentation` and offer a retry through `retryDownload`, so it goes through descriptor confirmation again.
- [ ] 3.5 For a completed record, verify file presence and classify it; refuse to render when the file is missing or the classification is not readable — including the demo placeholder — with cause-specific copy.
- [ ] 3.6 Offer reading again when the file is missing only if the paper's access is still a confirmed PDF; offer the external handoff when the file exists but cannot be rendered.
- [ ] 3.7 Implement windowed page rendering with a prefetch margin, a display-derived bitmap cache budget, and explicit recycling outside the window.
- [ ] 3.8 Cancel render requests for pages that leave the window rather than queueing them.
- [ ] 3.9 Implement pinch-to-zoom by re-rendering the visible page at the higher resolution rather than upscaling.
- [ ] 3.10 Present the current page number against the total and allow direct page navigation.
- [ ] 3.11 Restore page and zoom across rotation and process restart.
- [ ] 3.12 Persist the reading position on a debounce while reading, plus a final write when the screen leaves the composition.
- [ ] 3.13 Expose page navigation, zoom, transfer progress and the error states to accessibility services with meaningful labels and a logical traversal order.
- [ ] 3.14 Add tests: following a transfer to completion, a transfer failing while open, leaving mid-transfer (the download continues), multi-page navigation, resume at a stored position, resume after simulated process death, each refusal case.

## 4. Open routing from Downloads

- [ ] 4.1 Route Open on a completed download to the reader instead of the `ACTION_VIEW` intent.
- [ ] 4.2 Keep the external handoff as an explicit action on the record, preserving the content URI and temporary read permission, and keep the existing no-viewer-available feedback for it.
- [ ] 4.3 Confirm no unrestricted filesystem path is exposed by either route.
- [ ] 4.4 Add tests for both routes and for the fallback when in-app rendering fails.

## 5. Read PDF action

- [x] 5.1 Add `ReadPaperUseCase`: given a paper, return a reader target for a completed record with its file present or a queued/running record, and otherwise call `startDownload` and return a target only on success.
- [ ] 5.2 Replace `AccessAction.DownloadPdf` with `ReadPdf` in `toAccessAction()`, keeping the gate unchanged: only a confirmed `DirectPdf` yields it, and every other state still yields `null`. Label it "Read PDF", distinct from "Read online".
- [ ] 5.3 Wire the action in the search, detail and library ViewModels to `ReadPaperUseCase`, emitting a one-shot navigation event to `Reader(downloadId)` on a target and the shared download feedback on failure.
- [ ] 5.4 Add tests: the action is absent for every non-confirmed access state; a completed paper opens with no network call; a running transfer is reused rather than duplicated; a failed, canceled or file-missing record starts a confirmed transfer; a confirmation failure navigates nowhere and enqueues nothing.
- [ ] 5.5 Add a test that a paper whose access drops from confirmed to uncertain loses Read PDF on its card and detail even while its completed file is on the device, and stays reachable from Downloads.
- [ ] 5.6 Re-record the screenshot goldens affected by the label change with `:app:recordRoborazziDemoDebug`.

## 6. Continue reading

- [x] 6.1 Decide where the entry point lives — Downloads, Library, or both (see design open question). Decided: Library.
- [ ] 6.2 Implement it as a query over stored positions joined to downloads, filtered to papers whose file still exists, most recently read first.
- [ ] 6.3 Add tests: a paper whose file was removed never appears; ordering is by most recently read.

## 7. Validation

- [ ] 7.1 Test a long, high-resolution document under a constrained heap and confirm no `OutOfMemoryError` and no unbounded growth while scrolling.
- [ ] 7.2 Confirm the set of access states that yield an action is unchanged, that no access affordance or claim was added anywhere, and that no transfer starts without descriptor confirmation.
- [ ] 7.3 Confirm download validation, mappers, access resolution and the remote contract are unchanged.
- [ ] 7.4 Run `./gradlew :app:verifyAllFlavors`, `:app:verifyApiContract` and `:app:verifyConnected`, and compile every instrumentation source set — the shared `androidTest`, `androidTestHttp` and `androidTestIntegration` — since this change touches repository and navigation signatures.
- [ ] 7.5 Manually verify: Read PDF on a paper not yet downloaded, then leave mid-transfer and return; read, leave, return and resume; rotation mid-page; process death mid-page; Read PDF in the demo flavor ending at the placeholder refusal; opening after deleting the file; external handoff.
