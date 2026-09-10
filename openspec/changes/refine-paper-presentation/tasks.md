## 1. Grouped metadata model

- [ ] 1.1 Add a presentation model holding the attribution/venue line and the ordered publication-fact values plus optional primary topic, built by one function shared by card and detail.
- [ ] 1.2 Replace `formatMetadataLine` in `PaperCard.kt` with the grouped model; render attribution and venue on one line and the facts with the topic pill on the next.
- [ ] 1.3 Replace `formatDetailMetadataLine` in `PaperDetailScreen.kt` with a labelled facts grid built from the same model.
- [ ] 1.4 Omit any grouped line whose fields are all unavailable, with no placeholder punctuation and no empty row.
- [ ] 1.5 Group the metadata lines under a single semantics node so a card announces as one coherent item.
- [ ] 1.6 Add tests: complete metadata, missing venue, missing citation count, missing topic, and an entirely empty facts line.

## 2. Detail structure

- [ ] 2.1 Render the author list as its own block, summarised with a count of hidden authors and a control that reveals the complete list.
- [ ] 2.2 Clamp the abstract with an explicit control that reveals the remainder in place.
- [ ] 2.3 Move the confirmed access action into a bar pinned to the bottom of the detail scaffold; render nothing when there is no confirmed action.
- [ ] 2.4 Verify the pinned bar against gesture-navigation and display-cutout insets.
- [ ] 2.5 Add tests: long author list expands, long abstract expands, action reachable after scrolling to the end of the abstract, no bar rendered for a metadata-only paper.

## 3. Action emphasis

- [ ] 3.1 Render the confirmed PDF action as a full-width filled control using `primary` / `onPrimary`.
- [ ] 3.2 Render the confirmed open-full-text action as a full-width filled control using `tertiaryContainer` / `onTertiaryContainer`.
- [ ] 3.3 Confirm no new colour values are added to `Color.kt` and that both roles meet the contrast floor in light and dark themes.
- [ ] 3.4 Confirm every non-actionable access state still renders nothing — no badge, lock, label, source row, or disabled control.

## 4. Download state on paper surfaces

- [ ] 4.1 Add `PaperListItem(paper, download)` as an `@Immutable` presentation type.
- [ ] 4.2 Combine the papers flow with `DownloadsRepository` records in `SearchViewModel`, `LibraryViewModel`, and `PaperDetailViewModel`, keying by paper ID.
- [ ] 4.3 Change `PaperCard` to take `PaperListItem` and update the search and library call sites.
- [ ] 4.4 Add an optional download-state parameter to `AccessActionRow`, consulted only after `toAccessAction()` has yielded an action.
- [ ] 4.5 Render queued, running (determinate progress when byte counts are known), failed, and completed in the action slot, with cancel while queued or running.
- [ ] 4.6 Wire cancel from the paper surface to the same cancel path the Downloads screen uses.
- [ ] 4.7 Decide and implement whether a completed download replaces the action with Open or adds Open beside it (see design open question).
- [ ] 4.8 Add a test that pairs a running `DownloadRecord` with each non-actionable `AccessResolution` and asserts nothing renders.
- [ ] 4.9 Add tests: starting a download updates the originating surface immediately; the same paper shows identical state in Library and Downloads; cancel from a card reaches the terminal state.
- [ ] 4.10 Measure recomposition on a scrolling result list with an active download and confirm no per-frame churn.

## 5. Typography

- [ ] 5.1 Select the sans and serif families and verify their licences permit redistribution in a shipped APK; record the licences.
- [ ] 5.2 Add both families to `res/font/`, subset to Latin and only the weights used.
- [ ] 5.3 Split `Type.kt`: serif for card titles, detail title, and abstract; sans for every other style.
- [ ] 5.4 Re-derive card title `maxLines` and the abstract clamp length for the new family rather than carrying the current values over.
- [ ] 5.5 Measure the APK size delta and record it before and after.
- [ ] 5.6 Verify legibility and reflow at the largest supported font scale, in light and dark themes.
- [ ] 5.7 If the licence check in 5.1 fails, revert to `FontFamily.SansSerif` and close this group without other changes.

## 6. Validation

- [ ] 6.1 Confirm existing access-mapping tests pass unmodified.
- [ ] 6.2 Update `SearchScreenPreviews.kt` to cover: confirmed PDF, confirmed open full text, metadata-only, and each download state.
- [ ] 6.3 Run `:app:testDebugUnitTest`, `:app:connectedDebugAndroidTest`, `:app:lint`, and `:app:assembleDebug`.
- [ ] 6.4 Manually verify card and detail with every access variant, an active download, rotation, and process restart.
