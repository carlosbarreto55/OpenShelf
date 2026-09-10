## Why

Downloading a paper currently ends the app's involvement: the file is handed to whatever PDF viewer happens to be installed, the user leaves, and nothing about where they got to comes back. For an app whose whole point is finding and keeping papers, the moment of actually reading one is the moment it hands the user to someone else — and if no viewer is installed, the download was for nothing. Reading position is the piece that matters most: research papers are read across several sittings, and returning to page one every time is the reason a downloaded paper goes unread.

## What Changes

- **An in-app reader** for validated, completed downloads: paginated rendering, pinch-to-zoom, and page navigation.
- **Reading position is remembered per paper** and restored on reopen, locally and without an account.
- **A "continue reading" entry point** surfacing papers with a stored position.
- **Opening a completed download opens the reader by default**, with handing the file to an external viewer retained as an explicit, still-supported choice.
- **The reader refuses anything that is not a readable PDF** — including the demo placeholder file, which is a disclaimer text file and must never be presented as a paper. Encrypted and malformed files are reported honestly rather than rendered blank.
- No change to access rules, to how downloads are started or validated, or to the remote contract.

## Capabilities

### New Capabilities

- `in-app-reading`: opening a validated local PDF inside the app, page rendering and navigation, per-paper reading-position persistence, resuming, and honest refusal for files that cannot be read.

### Modified Capabilities

- `paper-downloads`: `Open a completed local PDF` changes — the default target becomes the in-app reader, with the external content-URI handoff retained as an explicit alternative and as the fallback when in-app rendering is not possible.

### Explicitly unchanged

- `verified-paper-access`: the reader is reachable only from a completed download, which is reachable only from an already-confirmed action. Nothing here creates a new route to content.
- `backend-ready-data-layer`: no contract change. Reading is entirely local.

> Sequencing: `paper-downloads` is still defined by the unarchived `build-openalex-paper-reader` change, so that delta assumes it lands (or is archived into `openspec/specs/`) first. This change is otherwise independent, and is the largest of the five — it should follow the UI changes rather than compete with them.

## Impact

- **Android code:** a new `ui/reader/` feature (screen, ViewModel, state), a new `data/reader/` page-rendering source, a route in `PaperReaderRoute`/`PaperReaderApp`, and changes to `ui/downloads/DownloadsScreen.kt` and `DownloadsViewModel.kt` where Open is currently wired to an `ACTION_VIEW` intent.
- **Rendering:** `android.graphics.pdf.PdfRenderer` from the platform, behind an interface so the implementation can be replaced. No new third-party dependency, no licence question.
- **Room:** a reading-position table keyed by paper, with the page index and an intra-page offset. Schema version bump and an additive migration.
- **Memory:** page bitmaps are the main cost; rendering is windowed and recycled rather than eager.
- **Tests:** rendering-source tests for a valid PDF, an encrypted PDF, a malformed file and the demo placeholder; position persistence across process death; Compose tests for navigation and resume.
- **Not affected:** search, access resolution, mappers, download workers, PDF validation at download time.
