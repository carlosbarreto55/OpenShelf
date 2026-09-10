## ADDED Requirements

### Requirement: Persistent search controls
The discovery screen SHALL keep the active query, the active filters, and the sort control reachable at every scroll position of the result list, without requiring the user to scroll back to the top.

#### Scenario: Refine after paging
- **WHEN** a user has scrolled past the first page of results and wants to change the query, remove a filter, or change the sort order
- **THEN** the query text, the active filters, and the sort control are visible and operable without scrolling the result list

#### Scenario: Controls reflect current state while scrolled
- **WHEN** the result list is scrolled away from the top
- **THEN** the persistent controls display the query, filters, and sort order currently applied to the visible results

#### Scenario: Editing from the scrolled position
- **WHEN** a user changes the query, a filter, or the sort order from the persistent controls while scrolled
- **THEN** the system applies the change and returns the result list to its first item

### Requirement: First-result visibility
The discovery screen SHALL present at least one complete result card within the first content viewport on supported phone widths at the default font scale, once results are available.

#### Scenario: Results are showing
- **WHEN** a search has returned at least one paper and the screen has not been scrolled
- **THEN** the first result card is fully visible without scrolling

#### Scenario: Introductory content yields to results
- **WHEN** results are present
- **THEN** introductory hero content does not occupy the viewport at the expense of the first result card

#### Scenario: Large font scale
- **WHEN** the user has increased system font scaling
- **THEN** the screen reflows without clipping essential text, and result visibility degrades gracefully rather than the layout breaking

### Requirement: Zero-query search content
When no query has been submitted, the discovery screen SHALL present content that can start a search, and SHALL NOT present placeholder copy that offers no action.

#### Scenario: No recent searches exist
- **WHEN** a user opens search with no submitted query and no recent-query history
- **THEN** the system offers suggested topics, each of which submits that topic as a real search when activated

#### Scenario: Recent searches exist
- **WHEN** a user opens search with no submitted query and recent queries are stored
- **THEN** the system offers the recent queries alongside the suggested topics, and activating either submits it as a search

#### Scenario: Suggestion is activated
- **WHEN** a user activates a suggested topic
- **THEN** the system submits it through the same query-normalisation and search path as typed input, and records it in recent searches
