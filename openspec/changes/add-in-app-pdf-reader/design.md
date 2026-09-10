## Context

`DownloadsRoute` collects `openFileRequestsFlow` and fires an `ACTION_VIEW` intent with the record's content URI, falling back to `onNoViewerAvailable` when nothing handles it. Downloads are already validated at completion — `paper-downloads` requires a MIME and file-signature check before a record is marked completed — and files are app-managed and served through a content URI, so the reader has a seekable local file to work with and does not need storage permissions.

The placeholder `FakeDownloadManager` deliberately writes a disclaimer text file rather than a fabricated PDF, and `DownloadRecord.mimeType` records the file's *actual* type for exactly this reason. Any reader must treat that file as unreadable, not render it.

minSdk is 26, so `android.graphics.pdf.PdfRenderer` is available on every supported device.

Constraints: no fabricated content, no new route to a paper that has no confirmed access, account-free, offline.

## Goals / Non-Goals

**Goals:**

- Reading a downloaded paper happens in the app, with position preserved across sittings and process death.
- Memory stays bounded on long documents.
- Files that cannot be rendered are reported, never faked.
- The external handoff survives as a real option, not a dead code path.

**Non-Goals:**

- Annotation, highlighting, or note-taking.
- Text selection, search within the document, or text extraction. `PdfRenderer` cannot do them; adding them means a different engine and a separate decision.
- Reflowed or night-mode rendering of page content.
- Reading anything that is not a completed local download — no streaming, no remote rendering.
- Any change to how downloads are started, validated or stored.

## Decisions

### 1. Render with the platform's `PdfRenderer`, behind an interface

`PdfRenderer` is on every supported device, adds no dependency, no APK weight and no licence question, and renders a page to a `Bitmap` at a chosen resolution — which is what page-based zoom needs.

Its constraints shape the design and are not negotiable: it needs a seekable `ParcelFileDescriptor`; only one page may be open at a time; it is not thread-safe; and it throws `SecurityException` for password-protected files.

It is therefore wrapped in a `PdfPageSource` interface (open, page count, render page at a target size, close) with a single-threaded dispatcher owning the renderer instance. Every constraint above is enforced in one class rather than being remembered at call sites. The interface also means `androidx.pdf` — currently alpha, and Fragment-based, which fits badly in a Compose-only app — can replace it later without touching the ViewModel or the screen.

Alternative rejected: a third-party rendering library. Better features, but it brings a native binary, a size cost and a licence review, for a first version whose job is "read the paper you downloaded".

### 2. Windowed rendering with explicit recycling

Pages render on demand for the visible window plus a small prefetch margin, into a bounded bitmap cache keyed by (page, render resolution). Pages outside the window are recycled. The cache is sized from the display's dimensions rather than a fixed page count, so a tablet and a phone behave sensibly.

Zoom re-renders the visible page at the higher resolution rather than upscaling the existing bitmap — upscaling is what makes a rendered PDF look like a photograph of a page. That is why the spec says "re-renders at a legible resolution" rather than "zooms".

### 3. Reading position is Room, written during reading, keyed by paper

A `reading_position` table keyed by `paperId`, holding the page index, a fractional intra-page offset, and an updated-at timestamp. Keyed by paper rather than by download, so re-downloading the same paper resumes where the user was.

Written on a debounce while reading, not only on exit — the spec requires surviving process death, and an app killed in the background never runs an exit path.

The fractional offset rather than a pixel offset means the position survives rotation, a display size change and a zoom change; a pixel offset would silently mean something different at a different width.

Cascade: removing a completed download and its file removes the stored position, so "continue reading" can never offer a paper whose file is gone.

### 4. "Continue reading" reads positions, not downloads

The entry point lists papers with a stored position whose file still exists, most recent first. It is a query over the position table joined to downloads — not a second concept — so it cannot drift from what is actually resumable.

Where it appears is deliberately left open: Downloads is the honest home, the Library is the more useful one. Decided before implementation, not during.

### 5. Unreadable files are classified, and the demo file is classified first

Opening runs a classification before any page renders: not a PDF by signature, encrypted, malformed, missing file, or readable. Each maps to specific copy and to the alternatives that still apply — the external handoff stays offered for a malformed file, because another viewer may be more tolerant, and is not offered for a missing file, because there is nothing to hand over.

The demo placeholder is caught by the signature check, since it is a text file. It is called out separately in the spec anyway, because "the reader must never present demo output as the paper" is the project's central rule applied to a new surface, and a check that exists only implicitly is a check that gets refactored away.

A page that fails to render mid-document marks that page unavailable rather than showing a blank one. A blank page is indistinguishable from a genuinely blank page in the paper — that ambiguity is the thing to avoid.

### 6. The reader is a pushed destination gated on the record, not on the paper

A new `PaperReaderRoute.Reader(downloadId)` — keyed by the download, since that is what guarantees a file exists. The ViewModel resolves the record, verifies completion and file presence, and only then opens the source. The route being reachable is not the authorisation; the record check is.

## Risks / Trade-offs

- [Bitmap memory on large or high-resolution documents] → Windowed rendering with explicit recycling and a display-derived cache budget; test with a long document and watch for `OutOfMemoryError` under a constrained heap.
- [`PdfRenderer`'s single-page, non-thread-safe contract is violated under fast scrolling] → All access serialised on one dispatcher inside `PdfPageSource`; render requests for pages that left the window are cancelled rather than queued.
- [No text selection or search will read as a missing feature] → Stated as a non-goal here and in the release notes; the interface leaves room for a different engine if it becomes the top complaint.
- [Position written too often causes write churn] → Debounced writes with a minimum interval, and a final write when the screen leaves the composition.
- [A stored position outlives its file] → Cascade on download removal, plus a file-existence check before offering "continue reading"; both, because either alone leaves a stale entry in some order of events.
- [The demo placeholder is rendered as a paper] → Signature classification before rendering, with an explicit test using the actual file `FakeDownloadManager` produces — not a synthetic stand-in.
- [Scope creeps toward annotation] → Non-goals are explicit; annotation would need its own data model and its own change.

## Migration Plan

1. Add `PdfPageSource` and its `PdfRenderer` implementation with classification, tested headlessly against a valid PDF, an encrypted one, a corrupt one, and the real placeholder file.
2. Add the reading-position table, migration and migration test.
3. Add the reader screen and ViewModel with windowed rendering, zoom and page navigation, gated on the download record.
4. Route Open to the reader; keep the external handoff as an explicit action and as the fallback.
5. Add "continue reading" once its home is decided.

Rollback: step 4 alone restores the current behaviour without removing anything else. Steps 1–3 are additive and unreachable if step 4 is reverted.

## Open Questions

- Where does "continue reading" live — Downloads, Library, or both? Library is more useful; Downloads is where the files are. Decide before step 5.
- Should the reader offer a night-mode inversion of page bitmaps? Cheap to implement, frequently wrong on figures and colour plates. Deferred.
- Should a paper opened in the reader be marked read, and should that appear in the Library? Interacts with the collections change; deferred until both have landed.
