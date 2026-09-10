## Context

`SearchScreen` is a single `LazyColumn` whose first three items are the brand/hero header, the query field, and the filter + sort row; results follow as further items. Everything therefore scrolls together. Cursor pagination is implemented and prefetches three items from the end, so a normal session scrolls well past the controls.

Above the `NavHost`, `PaperReaderApp` renders `DemoDataNotice` in a `Column` whenever `BackendConfiguration.mode == FAKE`. It is not dismissible and it costs vertical space on all four destinations.

Adding the values in `SearchScreen.kt` and `Theme.kt` — banner 64 + list padding 24 + header 122 + 24 + field 56 + 24 + chip row 40 + 24 + result count 20 + 24 — puts the first card 422px into a 764px content viewport (844 minus the 80px navigation bar), before system insets. These figures are arithmetic on source values, not device measurements.

The zero-query branch renders `InitialContent`, whose primary element is a `Surface` reading "Your research space is ready." — copy that neither explains the demo state nor starts a search.

Constraints: this change may not alter the fail-closed access mapping, the set of exposed filters, any remote contract, or the Room schema for papers. It also may not reintroduce OpenAlex calls or a topic taxonomy from the network — suggested topics must be a local, constant list until a backend can supply one.

## Goals / Non-Goals

**Goals:**

- Query, filters and sort remain operable at any scroll position.
- The first result card is fully visible in the first viewport at default font scale.
- Demo disclosure survives as a requirement while ceasing to be a permanent layout cost.
- The zero-query state can start a search.
- No regression in accessibility semantics, dark/light theming, or font scaling.

**Non-Goals:**

- Adding year, type or topic filter controls (tracked separately as backend-dependent debt).
- Changing card content or the access action's presentation (a separate change owns that).
- Changing loading, empty or error presentation (a separate change owns that).
- Any typography change.
- A real topic taxonomy from a backend.

## Decisions

### 1. Split the screen into a pinned control region and a scrolling result region

`SearchScreen` becomes a `Column`: a control region that is not part of the `LazyColumn`, and the `LazyColumn` of results beneath it. Hero content moves inside the control region and is hidden once results exist, driven by list scroll state rather than by content type, so that the transition is continuous rather than a jump.

Chosen over a `TopAppBar` with `enterAlwaysScrollBehavior`: the control region is not a title bar, it contains a text field, a horizontally scrollable chip row and a menu, and Material's app-bar scroll behaviours fight a nested horizontal scroll. Chosen over `stickyHeader`: a single sticky header would still leave the field and chips as separate scrolling items, and Compose's sticky headers do not compose well with the prefetch effect already reading `listState.layoutInfo`.

The collapse threshold reads `listState.firstVisibleItemIndex`/`firstVisibleItemScrollOffset`. Collapse must be idempotent and must not cause a layout feedback loop — the collapsed and expanded control regions differ in height, so the state that drives them is derived from the list, never from the control region's own measurement.

### 2. Expanded and collapsed control regions are two states of one composable, not two composables

A single `SearchControls` composable takes an `expanded: Boolean`. Expanded shows brand row, hero, full-height field, chip row and sort. Collapsed shows a reduced-height field carrying the query text, the same chip row, and the same sort control. Both render the identical semantics tree so that a screen-reader user is not handed a different control set depending on scroll position, and so a single set of Compose tests covers both.

Alternative rejected: swapping in a separate compact composable. It duplicates the filter and sort wiring, which is where the state bugs would be.

### 3. Density is bought back from the hero and the banner, not from touch targets

The 126px reclaimed comes from removing the pinned banner (64), collapsing the hero sub-line to a single line, and using the reference's tighter gaps between control rows. Touch targets stay at or above 48dp and `MaterialTheme.spacing` remains the only source of spacing values — this change may add tokens to `PaperReaderSpacing` but may not introduce literal `dp` values at call sites.

### 4. Demo disclosure moves to a dismissible first-run surface plus a header indicator

`DemoDataNotice` is removed from `PaperReaderApp`. In its place:

- A dismissible explanation shown on the search screen until acknowledged.
- A compact, always-present indicator in the search header that reopens the explanation.
- The at-the-point-of-consequence disclosure inside a simulated download, which already exists as `downloads_demo_placeholder_notice` and stays where it is.

Acknowledgement is a single boolean. It is stored in `DataStore` preferences rather than Room: it is not queried, joined or observed alongside paper data, and putting it in Room would mean a schema version bump and a migration for one flag. A small `DemoDisclosurePreferences` interface keeps the ViewModel free of Android storage types and keeps the flag fakeable in tests.

Alternative rejected: keeping the banner and making it dismissible in place. That preserves the 64px cost until the user finds the dismiss control, and leaves the notice on Library and Downloads where it explains nothing they can act on.

### 5. Suggested topics are a local constant, presented as ordinary queries

Suggestions are a fixed list of topic strings owned by the presentation layer and submitted through `SearchQuery.normalize` and the same `SearchPapersUseCase` path as typed input, so they appear in recent queries and go through the same debounce, cache and error handling. They carry no counts, no ranking and no claim of authority — the client has no source for any of that and must not fabricate one.

Alternative rejected: deriving suggestions from fixture topics. That would make demo data visible as product content and would produce a different suggestion set once a real backend lands.

### 6. `SearchUiState` gains presentation state, not new data sources

`SearchUiState` gains `suggestedTopics: List<String>` and `demoDisclosure: DemoDisclosureState` (`Hidden`, `FirstRun`, `IndicatorOnly`). Collapse is *not* in `SearchUiState`; it is local Compose state derived from `LazyListState`, because it is a property of the scroll position rather than of the domain, and hoisting it into the ViewModel would make every scroll frame a state emission.

## Risks / Trade-offs

- [The pinned control region eats the density it was meant to save] → The collapsed region is measured against the 48dp floor and the expanded region only exists while the list is at the top; verify the first-card position with a layout inspection before closing the change.
- [Collapse feedback loop causes jitter at the threshold] → Derive collapse from the list's scroll offset with hysteresis (a collapse threshold above the expand threshold), and cover it with a scroll test rather than relying on manual checking.
- [Dismissing the demo explanation hides the demo state from a reviewer who never scrolls to the header] → The compact indicator is always present in demo builds and is never dismissible; only the explanation is.
- [The disclosure requirement weakens] → The at-the-point-of-consequence disclosure inside downloads is explicitly out of scope for dismissal, so the strongest claim is never suppressible.
- [Suggested topics read as curated recommendations] → Present them as starting points with no counts or ordering claims; the copy must not imply the backend selected them.
- [Compose semantics regress when one control set has two visual states] → Both states emit the same semantics tree; assert it in the same test at both scroll positions.

## Migration Plan

1. Extract the control region from the `LazyColumn` with no visual change, keeping tests green — this isolates the structural edit from the design edit.
2. Add the collapsed state and the scroll-driven transition.
3. Introduce `DemoDisclosurePreferences` + DataStore, move the notice off `PaperReaderApp`, add the header indicator.
4. Replace `InitialContent` with suggestions plus recents.
5. Re-check first-card position and font-scale reflow.

Rollback is per step: each is independently revertible, and steps 3 and 4 touch disjoint files from steps 1 and 2.

## Open Questions

- Should the collapsed control region also appear on the Library screen once that screen gains its own filtering, or does Library get its own pattern? Deferred to the library change.
- What is the suggested-topic list? It must be defensible without a backend; a short set of broad fields is the safe default until the backend can rank them.
