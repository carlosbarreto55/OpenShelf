# Paper Discovery Specification

## Purpose
Account-free discovery of scholarly papers: topic search with debounced queries, filters and sorting, informative paper cards, paper details, cursor-paginated results, and accessible, adaptive screen states.

## Requirements

### Requirement: Account-free app entry
The application SHALL allow users to enter and use paper discovery without authentication, registration, or account prompts.

#### Scenario: First launch
- **WHEN** a user launches the application for the first time
- **THEN** the system displays the paper search experience without requiring identity information

### Requirement: Topic search
The application SHALL allow a user to submit a non-blank topic query and SHALL present papers matching the normalized search response.

#### Scenario: Successful topic search
- **WHEN** a user searches for `AI harness` and the data source returns matching papers
- **THEN** the system displays the result count and an ordered list of matching paper cards

#### Scenario: Blank query
- **WHEN** a user attempts to submit a blank or whitespace-only query
- **THEN** the system does not issue a search request and keeps the user focused on entering a topic

### Requirement: Debounced query updates
The application SHALL debounce typed query changes before requesting results and SHALL discard responses that no longer match the active query and filters.

#### Scenario: User types rapidly
- **WHEN** the user changes the query several times inside the debounce interval
- **THEN** the system requests results only for the settled query

#### Scenario: Older response finishes last
- **WHEN** a response for an earlier query arrives after the active query has changed
- **THEN** the system does not replace the active query's results with the older response

### Requirement: Search filters and sorting
The application SHALL expose accessible, combinable filters for open access, confirmed PDF availability, exact publication year, and canonical work type, and SHALL support relevance, newest, and citation sorting. Filter values and selected states SHALL survive sorting and pagination until individually cleared or all filters are reset.

#### Scenario: Apply confirmed PDF filter
- **WHEN** a user enables the PDF-available filter
- **THEN** the system shows only papers whose normalized access state contains a confirmed direct PDF

#### Scenario: Apply publication year
- **WHEN** a user selects a valid publication year
- **THEN** the next first-page request and normalized cache key include that exact year and the control exposes its selected value

#### Scenario: Apply work type
- **WHEN** a user selects Book chapter
- **THEN** the next first-page request uses the canonical `book_chapter` value and the control exposes its selected state

#### Scenario: Invalid publication year
- **WHEN** a user enters a year outside the supported contract range
- **THEN** the UI explains the validation problem and does not issue a search request with that value

#### Scenario: Change sort order
- **WHEN** a user changes sorting from relevance to newest
- **THEN** the system requests or reads results ordered by newest publication first and preserves the active query and filters

#### Scenario: Clear one filter
- **WHEN** a user clears publication year while another filter remains selected
- **THEN** only publication year is removed and the next result set retains the other filter

#### Scenario: Clear filters
- **WHEN** a user clears all filters
- **THEN** the system restores the unfiltered result set for the active query

### Requirement: Informative paper cards
Each paper card SHALL present the title, compact author attribution, venue when available, publication year, work type, citation count, and primary topic without requiring access to full text.

#### Scenario: Complete metadata
- **WHEN** a result contains every supported metadata field
- **THEN** the card presents those fields using the hierarchy represented by `docs/ui/lattice-search-reference.png`

#### Scenario: Optional metadata missing
- **WHEN** a result lacks a venue, citation count, or topic
- **THEN** the card omits the unavailable field without showing placeholder punctuation or breaking its layout

### Requirement: Discovery screen states
The application SHALL provide distinct initial, loading, content, empty, stale-content, and recoverable-error states. The loading state SHALL preserve the layout of the content it is replacing. The error state SHALL be determined by the failure's classified cause. The empty state SHALL identify which filters are narrowing the search.

#### Scenario: Search is loading
- **WHEN** a search is in flight and no results are yet available for the active query
- **THEN** the system presents placeholder content matching the shape of result cards, so that the arrival of results does not reflow the surrounding layout

#### Scenario: Search has no matches
- **WHEN** a completed search returns no papers
- **THEN** the system displays an empty state that retains the query and offers a clear way to adjust filters

#### Scenario: Empty result with active filters
- **WHEN** a completed search returns no papers and one or more filters are active
- **THEN** the system names each active filter, allows each to be removed individually, and offers a control that clears all of them at once

#### Scenario: Empty result with no active filters
- **WHEN** a completed search returns no papers and no filters are active
- **THEN** the system presents the empty state without implying that filters caused it

#### Scenario: Search fails with cached content
- **WHEN** a refresh fails and cached results exist for the active query
- **THEN** the system keeps the cached results visible and presents non-blocking stale or retry feedback

#### Scenario: Search fails without cached content
- **WHEN** a search fails and no cached results exist
- **THEN** the system displays a recoverable error state with a retry action

#### Scenario: Failure cause determines the error presented
- **WHEN** a search fails and the failure is classified with a cause
- **THEN** the system presents an explanation specific to that cause rather than a single generic message for every cause

#### Scenario: Offline failure
- **WHEN** a search fails because the device has no connectivity and no cached results exist
- **THEN** the system says so plainly and offers a route to locally available content in addition to any retry

#### Scenario: Non-recoverable failure
- **WHEN** a failure is classified as not recoverable
- **THEN** the system does not present a retry action that cannot succeed

#### Scenario: Error text stays safe
- **WHEN** any failure is presented
- **THEN** the system displays only the failure's safe user-facing message and never raw server payloads, stack traces, URLs, or credentials

### Requirement: Paper details
The application SHALL provide a paper-detail screen containing all available card metadata plus abstract and complete author information when supplied by the data source. A confirmed not-found response SHALL have a stable presentation distinct from recoverable service failure and malformed data.

#### Scenario: Open paper details
- **WHEN** a user selects a paper card
- **THEN** the system navigates to details for the selected stable paper ID

#### Scenario: Detail refresh fails
- **WHEN** detail refresh fails but cached paper metadata exists
- **THEN** the system displays the cached details and recoverable refresh feedback

#### Scenario: Paper is not found
- **WHEN** the detail endpoint returns HTTP 404 for the requested paper ID
- **THEN** the system presents a stable paper-not-found state, suppresses cached access actions, and does not label the result as a transient service failure

#### Scenario: Detail success body is malformed
- **WHEN** the detail endpoint returns HTTP 200 without a valid paper
- **THEN** the system presents a contract-data failure rather than the paper-not-found state

### Requirement: Adaptive and accessible presentation
The discovery UI SHALL follow Material 3 accessibility semantics, remain usable with scaled text and screen readers, support light and dark themes, and adapt without horizontal page overflow on supported phone widths.

#### Scenario: Screen reader navigation
- **WHEN** accessibility services inspect a result card and its controls
- **THEN** the system exposes meaningful labels, roles, selected states, and a logical traversal order

#### Scenario: Large font scale
- **WHEN** the user increases system font scaling
- **THEN** the search controls and paper metadata reflow without clipping essential text or actions

### Requirement: Truthful result counts
The discovery UI SHALL state the number of unique papers currently shown and SHALL state a matched total only when the data source supplied one. The application MUST NOT synthesize a matched total from loaded items or page size.

#### Scenario: Matched total is present
- **WHEN** the active query has 20 unique loaded papers and a supplied matched total of 125
- **THEN** the UI states that 20 of 125 papers are shown

#### Scenario: Matched total is absent
- **WHEN** the active query has loaded papers but no supplied total
- **THEN** the UI states only how many unique papers are shown and makes no claim about all matches

#### Scenario: Another page is appended
- **WHEN** a unique result page is appended
- **THEN** the shown count increases by the number of newly visible unique papers while the supplied total remains truthful

### Requirement: Pagination-ready results
The application SHALL load additional cursor-based result pages without duplicating papers and SHALL preserve first-seen backend order. Cursor progress SHALL remain available independently of whether the current page contains visible items, with repeated-cursor detection and a bounded number of automatic empty-page advances.

#### Scenario: Load next page
- **WHEN** the user approaches the end of a result list and a next cursor exists
- **THEN** the system requests the next page and appends unique papers in backend order

#### Scenario: Next page fails
- **WHEN** an additional page fails to load
- **THEN** the system retains existing results and presents a retry control for that page

#### Scenario: Empty page has a next cursor
- **WHEN** a page contains no valid visible papers and supplies a new next cursor
- **THEN** the system advances automatically within the configured bound instead of treating the query as terminal

#### Scenario: Later page contains results
- **WHEN** one or more empty cursor pages are followed within the bound by a non-empty page
- **THEN** the system presents the later unique papers without first presenting a terminal empty state

#### Scenario: Empty-page bound is reached
- **WHEN** the configured automatic advance bound is reached and another new cursor remains
- **THEN** the system retains the cursor and presents a non-terminal retry state rather than claiming there are no matches or continuing indefinitely

#### Scenario: Cursor repeats
- **WHEN** a response repeats a cursor already visited in the active query session
- **THEN** the system stops automatic loading, retains existing results, and reports a recoverable pagination contract failure without issuing a loop

#### Scenario: Pages overlap by paper ID
- **WHEN** a later page repeats a paper ID already loaded and also contains new IDs
- **THEN** the repeated paper is not added again and each new paper is appended in its original backend order

#### Scenario: Incoming page repeats an ID
- **WHEN** one page contains the same stable paper ID more than once
- **THEN** only its first occurrence participates in visible and cached result order
