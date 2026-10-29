## Why

Reading a paper currently takes two separate acts: download it, then go to Downloads and open it, at which point the file is handed to whatever PDF viewer happens to be installed, the user leaves, and nothing about where they got to comes back. For an app whose whole point is finding and keeping papers, the moment of actually reading one is the moment it hands the user to someone else — and if no viewer is installed, the download was for nothing. "Download" is a means, not the goal: the user wants to read. Reading position is the piece that matters most once they do: research papers are read across several sittings, and returning to page one every time is the reason a downloaded paper goes unread.

## What Changes

- **A "Read PDF" action replaces "Download PDF"** wherever a paper has confirmed `DirectPdf` access — search cards, detail and library alike, through the same fail-closed mapping point. Activating it fetches the paper through the existing download pipeline and opens it in the app. There is no separate Download action any more; reading a paper *is* how it gets onto the device.
- **The transfer still goes through the download pipeline, unchanged.** Every transfer is still preceded by a fresh `GET /v1/papers/{id}/download` confirmation, validated as a PDF before it completes, and recorded as an ordinary download record — so the paper appears in Downloads, stays readable offline, and is removed by "Clear completed" like any other.
- **An in-app reader** for validated, completed downloads: paginated rendering, pinch-to-zoom, and page navigation. While the transfer is still running, the reader shows its progress and opens the document when it completes.
- **Reading position is remembered per paper** and restored on reopen, locally and without an account.
- **A "continue reading" entry point** surfacing papers with a stored position.
- **Opening a completed download opens the reader by default**, with handing the file to an external viewer retained as an explicit, still-supported choice.
- **The reader refuses anything that is not a readable PDF** — including the demo placeholder file, which is a disclaimer text file and must never be presented as a paper. Encrypted and malformed files are reported honestly rather than rendered blank.
- No change to which access states yield an action, to how transfers are confirmed or validated, or to the remote contract.

## Capabilities

### New Capabilities

- `in-app-reading`: starting a read from a confirmed PDF action, following the transfer into the reader, opening a validated local PDF inside the app, page rendering and navigation, per-paper reading-position persistence, resuming, and honest refusal for files that cannot be read.

### Modified Capabilities

- `verified-paper-access`: `Confirmed PDF action` changes — the action displayed for a confirmed `DIRECT_PDF` state is Read PDF instead of Download PDF. The gate is identical: only a valid backend-confirmed descriptor yields it, and every other state still renders nothing.
- `paper-downloads`: `Start only verified PDF downloads` changes — the user-facing trigger is the Read PDF action. `Open a completed local PDF` changes — the default target becomes the in-app reader, with the external content-URI handoff retained as an explicit alternative and as the fallback when in-app rendering is not possible.

### Explicitly unchanged

- Fail-closed access rendering: the set of states that yield an action, and the single mapping point `toAccessAction()`, are unchanged — only the label and destination of the `DirectPdf` action change. A local copy of a paper whose access is no longer confirmed stays reachable from Downloads and "continue reading", never through a new access affordance.
- Transfer-time descriptor confirmation and PDF validation: reading never bypasses either; it calls the same repository operation the Download action called.
- `backend-ready-data-layer`: no contract change. Rendering and reading position are entirely local.

> Sequencing: this change is independent of the other active changes in substance, but it overlaps two of them in code. `refine-paper-presentation` restyles `AccessActionRow` and threads download state into it; whichever lands second rebases the label and colour of the `DirectPdf` action onto the other. `add-library-collections` also adds a Room migration; neither change owns a fixed schema version — each takes the next free version on `main` when it is implemented. This remains the largest of the active changes and should follow the UI changes rather than compete with them.

## Impact

- **Android code:** a new `ui/reader/` feature (screen, ViewModel, state), a new `data/reader/` page-rendering source, a `Reader` route in `PaperReaderRoute`/`PaperReaderApp`, and changes to `ui/components/AccessPresentation.kt` (the `DirectPdf` action becomes Read PDF), the search, detail and library ViewModels where the action was wired to `startDownload` (it now also navigates to the reader), and `ui/downloads/DownloadsScreen.kt` and `DownloadsViewModel.kt` where Open is currently wired to an `ACTION_VIEW` intent.
- **Rendering:** `android.graphics.pdf.PdfRenderer` from the platform, behind an interface so the implementation can be replaced. No new third-party dependency, no licence question. `androidx.pdf` was re-evaluated and deferred — see design decision 1.
- **Room:** a reading-position table keyed by paper, with the page index and an intra-page offset. Schema version bump to the next free version and an additive migration.
- **Memory:** page bitmaps are the main cost; rendering is windowed and recycled rather than eager.
- **Tests:** rendering-source tests for a valid PDF, an encrypted PDF and a malformed file; a demo-flavor test against the actual placeholder file; the Read action across every access state; following a transfer into the reader; position persistence across process death; Compose tests for navigation and resume.
- **Not affected:** search, access resolution, mappers, download workers, descriptor confirmation, PDF validation at download time.
