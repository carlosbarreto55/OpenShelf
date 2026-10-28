## ADDED Requirements

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

## MODIFIED Requirements

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
