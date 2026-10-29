## Context

`AccessActionRow` renders the action `toAccessAction()` yields: `DownloadPdf` for a confirmed `DirectPdf`, `ReadOnline` for a confirmed `OpenFullText`, nothing otherwise. The `DownloadPdf` action is wired to `startDownload` in the search, detail and library ViewModels, which call `DownloadsRepository.startDownload(paper)` — the operation that performs transfer-time descriptor confirmation before enqueueing. Nothing else happens: the user has to go to Downloads to open the result.

`DownloadsRoute` collects `openFileRequestsFlow` and fires an `ACTION_VIEW` intent with the record's content URI, falling back to `onNoViewerAvailable` when nothing handles it. Downloads are already validated at completion — `paper-downloads` requires a MIME and file-signature check before a record is marked completed — and files are app-managed and served through a content URI, so the reader has a seekable local file to work with and does not need storage permissions. A download record's id is the paper's id in both gateways, so a paper has at most one record.

The demo `FakeDownloadManager`, which lives in the `demo` source set, deliberately writes a disclaimer text file and records `mimeType = "text/plain"` rather than a fabricated PDF. Any reader must treat that file as unreadable, not render it.

minSdk is 26 and compileSdk is 36.1, so `android.graphics.pdf.PdfRenderer` is available on every supported device.

Constraints: no fabricated content, no new route to a paper that has no confirmed access, no transfer without fresh descriptor confirmation, account-free, offline once the file is on the device.

## Goals / Non-Goals

**Goals:**

- Reading is one action: the user taps Read PDF and ends up reading, without a separate download step.
- Reading a downloaded paper happens in the app, with position preserved across sittings and process death.
- Every byte read still arrives through the existing confirmed, validated download pipeline.
- Memory stays bounded on long documents.
- Files that cannot be rendered are reported, never faked.
- The external handoff survives as a real option, not a dead code path.

**Non-Goals:**

- Annotation, highlighting, or note-taking.
- Text selection, search within the document, or text extraction. `PdfRenderer` cannot do them; adding them means a different engine — see decision 1.
- Reflowed or night-mode rendering of page content.
- Streaming or progressive rendering: the reader renders only a complete, validated file. While a transfer runs, it shows progress, not partial pages.
- Reading a paper whose access is not a currently confirmed `DirectPdf` through any new affordance.
- Any change to how downloads are confirmed, validated or stored.

## Decisions

### 1. Render with the platform's `PdfRenderer`, behind an interface — `androidx.pdf` re-evaluated and deferred

`PdfRenderer` is on every supported device, adds no dependency, no APK weight and no licence question, and renders a page to a `Bitmap` at a chosen resolution — which is what page-based zoom needs.

Its constraints shape the design and are not negotiable: it needs a seekable `ParcelFileDescriptor`; only one page may be open at a time; it is not thread-safe; and it throws `SecurityException` for password-protected files.

It is therefore wrapped in a `PdfPageSource` interface (open, page count, render page at a target size, close) with a single-threaded dispatcher owning the renderer instance. Every constraint above is enforced in one class rather than being remembered at call sites.

**`androidx.pdf`, as of 2026-10-27.** The library has reached `1.0.0-beta01` (2026-08-26) and now ships a Compose `PdfViewer`, with text selection, find-in-file and link handling — the features this design lists as non-goals, and the ones a reader of papers will miss first. It is still not adopted, for three concrete reasons:

- Its read and rendering features are backported only to API 28; this app's minSdk is 26, so devices on 26–27 would need `PdfRenderer` anyway, or minSdk would have to rise.
- The Compose artifact requires compileSdk 37; the project is on 36.1. (AGP 9.2.1 already meets its AGP requirement.)
- `PdfViewer` and `PdfViewerState` are `@ExperimentalPdfApi` in the beta.

Be precise about what the interface buys: `PdfPageSource` isolates *rendering*, so another page renderer drops in without touching the screen. `PdfViewer` is not a renderer but a whole viewer — adopting it would replace the page list and zoom handling in the screen too, while the ViewModel's gating, classification, progress-following and position persistence would survive. Revisit when `androidx.pdf` is stable and the project is ready to take compileSdk 37 and decide the minSdk question; text selection or search becoming the top complaint is the trigger.

Alternative rejected: a third-party rendering library. Better features, but it brings a native binary, a size cost and a licence review, for a first version whose job is "read the paper".

### 2. Windowed rendering with explicit recycling

Pages render on demand for the visible window plus a small prefetch margin, into a bounded bitmap cache keyed by (page, render resolution). Pages outside the window are recycled. The cache is sized from the display's dimensions rather than a fixed page count, so a tablet and a phone behave sensibly.

Zoom re-renders the visible page at the higher resolution rather than upscaling the existing bitmap — upscaling is what makes a rendered PDF look like a photograph of a page. That is why the spec says "re-renders at a legible resolution" rather than "zooms".

### 3. Reading position is Room, written during reading, keyed by paper

A `reading_position` table keyed by `paperId`, holding the page index, a fractional intra-page offset, and an updated-at timestamp. Keyed by paper rather than by download, so re-reading a paper whose file was cleared resumes where the user was once it is fetched again.

Written on a debounce while reading, not only on exit — the spec requires surviving process death, and an app killed in the background never runs an exit path.

The fractional offset rather than a pixel offset means the position survives rotation, a display size change and a zoom change; a pixel offset would silently mean something different at a different width.

Cascade: removing a completed download and its file removes the stored position, so "continue reading" can never offer a paper whose file is gone.

The schema version is **not pre-assigned**. The database is at version 3 today, and `add-library-collections` also adds a migration. Whichever change is implemented first takes 4 and the other takes the next; the migration is written against the schema actually on `main` at implementation time, and the migration test runs from every exported earlier version, not only from 3.

### 4. "Continue reading" reads positions, not downloads

The entry point lists papers with a stored position whose file still exists, most recent first. It is a query over the position table joined to downloads — not a second concept — so it cannot drift from what is actually resumable.

It lives in the **Library** (decided 2026-10-28, before implementation): Downloads is where the files are, but the Library is where a user goes to get back to their papers. Downloads keeps listing every file, so no resumable paper is reachable only from the Library.

### 5. Unreadable files are classified, and the demo file is classified first

Opening runs a classification before any page renders: not a PDF by signature, encrypted, malformed, missing file, or readable. Each maps to specific copy and to the alternatives that still apply — the external handoff stays offered for a malformed file, because another viewer may be more tolerant, and is not offered for a missing file, because there is nothing to hand over.

The demo placeholder is caught by the signature check, since it is a text file. It is called out separately in the spec anyway, because "the reader must never present demo output as the paper" is the project's central rule applied to a new surface, and a check that exists only implicitly is a check that gets refactored away. Its test uses the actual file `FakeDownloadManager` writes, so it lives in `testDemo` — the class does not exist in the HTTP flavors. The classifier itself and its PDF, encrypted and corrupt cases are flavor-independent and stay in `test`.

In the demo flavor this means Read PDF always ends at the demo refusal. That is the honest outcome and is accepted.

A page that fails to render mid-document marks that page unavailable rather than showing a blank one. A blank page is indistinguishable from a genuinely blank page in the paper — that ambiguity is the thing to avoid.

### 6. The reader is a pushed destination keyed by the download record

A new `PaperReaderRoute.Reader(downloadId)` — keyed by the download, because that is what carries the file and the transfer state. The ViewModel observes the record and renders by its state: queued or running → progress with cancel; failed → the cause, through `ErrorPresentation`, with a retry that goes through `retryDownload` and therefore through confirmation again; canceled → a way back; completed → verify the file exists, classify it, and only then open the source. The route being reachable is not the authorisation; the record check is.

The route is only ever navigated to once a record exists, so the reader never has to handle "no record" as a normal state — it treats it as a missing-file refusal.

### 7. Read PDF replaces Download PDF and resolves to a reader target

`toAccessAction()` keeps its shape and its gate: a confirmed `DirectPdf` yields one action, now `ReadPdf` instead of `DownloadPdf`, and every other state still yields `null`. There is deliberately no second "Download" action — reading puts the paper on the device, so a separate download would be a redundant button with the same effect. The copy is "Read PDF", distinct from the existing "Read online", which opens the publisher's HTML in the browser.

Activating it calls a `ReadPaperUseCase` shared by the search, detail and library ViewModels:

```
Read PDF ──▶ ReadPaperUseCase(paper)
               │
               ├─ record COMPLETED and file exists ─▶ Reader(id)        no network
               ├─ record QUEUED or RUNNING         ─▶ Reader(id)        follow existing transfer
               └─ none / FAILED / CANCELED /
                  COMPLETED but file missing       ─▶ startDownload(paper)
                                                        │  (fresh GET /download confirmation)
                                                        ├─ Success ─▶ Reader(id)   follows progress
                                                        └─ Failure ─▶ stay; shared error feedback
```

The use case returns a target, not a navigation: the ViewModel emits a one-shot navigation event, as `DownloadsViewModel` already does for open-file requests. Navigating only on a created record is what keeps decision 6's invariant true.

A tap on a search card navigates from search to the reader exactly as a tap on detail does; the action row is shared, so the behaviour is too.

**A local copy is not an access affordance.** If a paper's access stops being a confirmed `DirectPdf`, the stale-affordance rule removes Read PDF from its card and detail — even when a completed file is on the device. That copy stays reachable from Downloads and from "continue reading", which are lists of files the user already has, not access decisions. Showing an action from a local file on a paper whose access is unconfirmed would be the first access-shaped control driven by something other than the confirmed resolution, and the fail-closed rule exists to prevent exactly that.

The transfer continues when the user leaves the reader. It is an ordinary download: it shows in Downloads under "in progress", then under "on this device", and "Clear completed" removes it, its file and its reading position together.

## Risks / Trade-offs

- [Bitmap memory on large or high-resolution documents] → Windowed rendering with explicit recycling and a display-derived cache budget; test with a long document and watch for `OutOfMemoryError` under a constrained heap.
- [`PdfRenderer`'s single-page, non-thread-safe contract is violated under fast scrolling] → All access serialised on one dispatcher inside `PdfPageSource`; render requests for pages that left the window are cancelled rather than queued.
- [No text selection or search will read as a missing feature] → Stated as a non-goal here and in the release notes; decision 1 records the concrete conditions under which `androidx.pdf` replaces the renderer.
- [Read PDF on a slow network leaves the user staring at a progress bar] → The reader shows determinate progress and cancel; leaving does not lose the transfer. Streaming is a non-goal, so this is the honest cost of rendering only validated files.
- [Users who relied on "Download for later" no longer see a Download button] → Read PDF downloads the paper too, and leaving the reader immediately keeps the transfer running. If this proves confusing, the Downloads screen's empty state is where to explain it — not a second action.
- [Double-tap starts two transfers] → `ReadPaperUseCase` reuses a queued or running record; a record's id is the paper's id, so there is at most one.
- [Overlap with `refine-paper-presentation`, which restyles `AccessActionRow` and threads download state into it] → Whichever lands second rebases onto the other; the fail-closed ordering (map first, then consult download state) is shared by both and must survive the rebase.
- [Position written too often causes write churn] → Debounced writes with a minimum interval, and a final write when the screen leaves the composition.
- [A stored position outlives its file] → Cascade on download removal, plus a file-existence check before offering "continue reading"; both, because either alone leaves a stale entry in some order of events.
- [The demo placeholder is rendered as a paper] → Signature classification before rendering, with an explicit `testDemo` test using the actual file `FakeDownloadManager` produces — not a synthetic stand-in.
- [Scope creeps toward annotation] → Non-goals are explicit; annotation would need its own data model and its own change.

## Migration Plan

1. Add `PdfPageSource` and its `PdfRenderer` implementation with classification, tested headlessly against a valid PDF, an encrypted one and a corrupt one, plus the real placeholder file under `testDemo`.
2. Add the reading-position table, migration and migration test, at the next free schema version.
3. Add the reader screen and ViewModel with windowed rendering, zoom, page navigation and transfer-following, gated on the download record.
4. Route Open in Downloads to the reader; keep the external handoff as an explicit action and as the fallback.
5. Add `ReadPaperUseCase` and switch the `DirectPdf` action from Download PDF to Read PDF in `toAccessAction()` and every ViewModel that handles it.
6. Add "continue reading" once its home is decided.

Rollback: reverting step 5 restores Download PDF without removing anything else; reverting step 4 as well restores the external handoff. Steps 1–3 are additive and unreachable if steps 4 and 5 are reverted.

## Open Questions

- Should the reader offer a night-mode inversion of page bitmaps? Cheap to implement, frequently wrong on figures and colour plates. Deferred.
- Should a paper opened in the reader be marked read, and should that appear in the Library? Interacts with the collections change; deferred until both have landed.
