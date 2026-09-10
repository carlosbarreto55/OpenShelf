## 1. Rendering source

- [ ] 1.1 Add a `PdfPageSource` interface: open a managed file, report page count, render a page at a target size, close.
- [ ] 1.2 Implement it with `android.graphics.pdf.PdfRenderer`, serialising every call on a single dedicated dispatcher so the one-page-at-a-time and thread-safety constraints are enforced in one place.
- [ ] 1.3 Add open-time classification: not a PDF by file signature, encrypted, malformed, file missing, or readable.
- [ ] 1.4 Add tests for a valid multi-page PDF, an encrypted PDF, a corrupt file, a missing file, and the actual file `FakeDownloadManager` produces — not a synthetic stand-in.
- [ ] 1.5 Add a test that a page failing to render is reported as unavailable rather than returning a blank bitmap.

## 2. Reading position persistence

- [ ] 2.1 Add a `reading_position` entity keyed by paper: page index, fractional intra-page offset, updated-at.
- [ ] 2.2 Cascade removal so deleting a completed download and its file removes the stored position.
- [ ] 2.3 Bump the Room schema version, write the additive migration and a migration test asserting saved papers, collections if present, and download records survive.
- [ ] 2.4 Add DAO operations: read a paper's position, upsert it, observe resumable papers ordered by most recently read.
- [ ] 2.5 Add tests for upsert, cascade on download removal, and ordering of resumable papers.

## 3. Reader screen

- [ ] 3.1 Add `PaperReaderRoute.Reader(downloadId)` and wire it into the `NavHost` as a pushed destination.
- [ ] 3.2 Add the reader ViewModel: resolve the record, verify completion and file presence, classify the file, and expose an immutable reader state.
- [ ] 3.3 Refuse to render for any record that is not completed, whose file is missing, or whose classification is not readable — including the demo placeholder — with cause-specific copy.
- [ ] 3.4 Offer "download again" when the file is missing and the access descriptor is still valid; offer the external handoff when the file exists but cannot be rendered.
- [ ] 3.5 Implement windowed page rendering with a prefetch margin, a display-derived bitmap cache budget, and explicit recycling outside the window.
- [ ] 3.6 Cancel render requests for pages that leave the window rather than queueing them.
- [ ] 3.7 Implement pinch-to-zoom by re-rendering the visible page at the higher resolution rather than upscaling.
- [ ] 3.8 Present the current page number against the total and allow direct page navigation.
- [ ] 3.9 Restore page and zoom across rotation and process restart.
- [ ] 3.10 Persist the reading position on a debounce while reading, plus a final write when the screen leaves the composition.
- [ ] 3.11 Expose page navigation, zoom and the error states to accessibility services with meaningful labels and a logical traversal order.
- [ ] 3.12 Add tests: multi-page navigation, resume at a stored position, resume after simulated process death, each refusal case.

## 4. Open routing

- [ ] 4.1 Route Open on a completed download to the reader instead of the `ACTION_VIEW` intent.
- [ ] 4.2 Keep the external handoff as an explicit action on the record, preserving the content URI and temporary read permission, and keep the existing no-viewer-available feedback for it.
- [ ] 4.3 Confirm no unrestricted filesystem path is exposed by either route.
- [ ] 4.4 Confirm the reader is unreachable for any paper without a completed download record.
- [ ] 4.5 Add tests for both routes and for the fallback when in-app rendering fails.

## 5. Continue reading

- [ ] 5.1 Decide where the entry point lives — Downloads, Library, or both (see design open question).
- [ ] 5.2 Implement it as a query over stored positions joined to downloads, filtered to papers whose file still exists, most recently read first.
- [ ] 5.3 Add tests: a paper whose file was removed never appears; ordering is by most recently read.

## 6. Validation

- [ ] 6.1 Test a long, high-resolution document under a constrained heap and confirm no `OutOfMemoryError` and no unbounded growth while scrolling.
- [ ] 6.2 Confirm nothing in this change creates a route to a paper that has no confirmed access, and that no access affordance or claim was added anywhere.
- [ ] 6.3 Confirm downloads, validation, mappers, access resolution and the remote contract are unchanged.
- [ ] 6.4 Run `:app:testDebugUnitTest`, `:app:connectedDebugAndroidTest`, `:app:lint`, `:app:assembleDebug`, and the Room schema check.
- [ ] 6.5 Manually verify: read, leave, return and resume; rotation mid-page; process death mid-page; opening the demo placeholder; opening after deleting the file; external handoff.
