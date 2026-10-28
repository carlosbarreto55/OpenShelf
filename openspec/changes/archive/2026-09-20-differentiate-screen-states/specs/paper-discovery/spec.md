## MODIFIED Requirements

### Requirement: Topic search
The application SHALL allow a user to submit a non-blank topic query and SHALL present papers matching the normalized search response. Any displayed count SHALL state how many results are currently shown, and SHALL state the matched total only when the data source has supplied one.

#### Scenario: Successful topic search
- **WHEN** a user searches for `AI harness` and the data source returns matching papers
- **THEN** the system displays the result count and an ordered list of matching paper cards

#### Scenario: Count after loading a further page
- **WHEN** the user loads an additional page of results
- **THEN** the displayed count reflects the larger number of results now shown, and never presents a loaded count as if it were the matched total

#### Scenario: Matched total is supplied
- **WHEN** the data source supplies a matched total for the active query
- **THEN** the system presents both how many results are shown and the matched total

#### Scenario: Matched total is absent
- **WHEN** the data source supplies no matched total
- **THEN** the system presents only how many results are shown, and asserts nothing about the total

#### Scenario: Blank query
- **WHEN** a user attempts to submit a blank or whitespace-only query
- **THEN** the system does not issue a search request and keeps the user focused on entering a topic

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
