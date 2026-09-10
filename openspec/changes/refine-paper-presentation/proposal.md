## Why

A paper's metadata is currently flattened into a single dot-joined sentence — `formatMetadataLine` joins authors, venue, year, type and citation count into one `bodyMedium` line — so with real data it wraps into two or three lines of undifferentiated grey directly beneath the title, and the title stops anchoring the card. The paper-detail screen does the same thing again. Meanwhile the confirmed access action, which is the payoff of the entire access-verification architecture, renders as a `TextButton` — the lowest-emphasis button Material 3 offers — and starting a download produces no visible change on the paper itself, even though `paper-downloads` already requires the paper UI and the Downloads screen to observe the same progress state. Finally, every text style in `Type.kt` is `FontFamily.SansSerif`, which makes an app whose entire content is scholarly prose look like the Material 3 default.

## What Changes

- **Restructure card metadata into two grouped lines**: attribution and venue on one line, then publication facts (year, type, citations) with the primary topic as a trailing pill — the grouping the reference image already shows and the current spec already points at.
- **Restructure paper detail** to match: authors as their own expandable block, publication facts in a scannable grid rather than a joined sentence, and the abstract clamped with an explicit expand control.
- **Raise the emphasis of the confirmed access action** to a full-width filled control, using the primary role for a confirmed PDF and the tertiary container role for confirmed open full text. The fail-closed mapping in `AccessResolution.toAccessAction()` is unchanged — only the presentation of an already-granted action changes.
- **Give the paper's action position a download state.** When a download record exists for that paper, the same slot presents queued / running (with determinate progress when byte counts are known) / failed / completed, plus cancel and a route to Downloads.
- **Move the confirmed action on paper detail into a persistent bar** so it does not scroll away behind the abstract.
- **Split typography between chrome and content**: a sans family for UI chrome, a reading serif for paper titles and abstracts.
- No change to which access states produce an action, to the remote contract, or to the download lifecycle itself.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `paper-discovery`: `Informative paper cards` gains explicit grouping requirements; `Paper details` gains requirements for how detail metadata, authors and the abstract are structured.
- `paper-downloads`: `Observable download lifecycle` becomes explicit that the paper's own action position — not only the Downloads screen — presents the current state and offers cancel.

### Explicitly unchanged

- `verified-paper-access` is untouched. Which states yield an action, and the silence of every other state, are unaffected by this change.

> Sequencing: these capabilities are still defined by the unarchived `build-openalex-paper-reader` change, so these deltas assume that change lands (or is archived into `openspec/specs/`) first. This change also assumes `restructure-search-screen-chrome`, since both touch `SearchScreen.kt`; land that one first to avoid a merge conflict in the control region.

## Impact

- **Android code:** `ui/components/PaperCard.kt` (metadata grouping, action slot), `ui/components/AccessPresentation.kt` (filled action, download-state rendering), `ui/paperdetail/PaperDetailScreen.kt` (facts grid, author block, abstract clamp, persistent action bar), `ui/paperdetail/PaperDetailViewModel.kt` and `ui/search/SearchViewModel.kt` and `ui/library/LibraryViewModel.kt` (download record reaches the paper surfaces), `ui/theme/Type.kt`, `ui/search/SearchScreenPreviews.kt`.
- **Fonts:** two bundled font families added to `app/src/main/res/font/`, increasing APK size. Licences must permit redistribution.
- **Domain:** no model changes. `DownloadRecord` already carries everything the card needs; it is joined to the paper at the presentation layer, not merged into `Paper`.
- **Tests:** card and detail Compose tests for the grouped layout and each download state; existing access-mapping tests must continue to pass unmodified.
- **Not affected:** DTOs, mappers, repositories, Room schema, download workers, access resolution.
