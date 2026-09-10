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
The application SHALL support combinable filters for open access, confirmed PDF availability, publication year, and work type, and SHALL support relevance, newest, and citation sorting.

#### Scenario: Apply confirmed PDF filter
- **WHEN** a user enables the PDF-available filter
- **THEN** the system shows only papers whose normalized access state contains a confirmed direct PDF

#### Scenario: Change sort order
- **WHEN** a user changes sorting from relevance to newest
- **THEN** the system requests or reads results ordered by newest publication first and preserves the active query and filters

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
The application SHALL provide distinct initial, loading, content, empty, stale-content, and recoverable-error states.

#### Scenario: Search has no matches
- **WHEN** a completed search returns no papers
- **THEN** the system displays an empty state that retains the query and offers a clear way to adjust filters

#### Scenario: Search fails with cached content
- **WHEN** a refresh fails and cached results exist for the active query
- **THEN** the system keeps the cached results visible and presents non-blocking stale or retry feedback

#### Scenario: Search fails without cached content
- **WHEN** a search fails and no cached results exist
- **THEN** the system displays a recoverable error state with a retry action

### Requirement: Paper details
The application SHALL provide a paper-detail screen containing all available card metadata plus abstract and complete author information when supplied by the data source.

#### Scenario: Open paper details
- **WHEN** a user selects a paper card
- **THEN** the system navigates to details for the selected stable paper ID

#### Scenario: Detail refresh fails
- **WHEN** detail refresh fails but cached paper metadata exists
- **THEN** the system displays the cached details and recoverable refresh feedback

### Requirement: Adaptive and accessible presentation
The discovery UI SHALL follow Material 3 accessibility semantics, remain usable with scaled text and screen readers, support light and dark themes, and adapt without horizontal page overflow on supported phone widths.

#### Scenario: Screen reader navigation
- **WHEN** accessibility services inspect a result card and its controls
- **THEN** the system exposes meaningful labels, roles, selected states, and a logical traversal order

#### Scenario: Large font scale
- **WHEN** the user increases system font scaling
- **THEN** the search controls and paper metadata reflow without clipping essential text or actions

### Requirement: Pagination-ready results
The application SHALL load additional cursor-based result pages without duplicating papers and SHALL preserve the established order.

#### Scenario: Load next page
- **WHEN** the user approaches the end of a result list and a next cursor exists
- **THEN** the system requests the next page and appends unique papers in backend order

#### Scenario: Next page fails
- **WHEN** an additional page fails to load
- **THEN** the system retains existing results and presents a retry control for that page
