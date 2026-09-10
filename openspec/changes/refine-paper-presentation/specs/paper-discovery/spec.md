## MODIFIED Requirements

### Requirement: Informative paper cards
Each paper card SHALL present the title, compact author attribution, venue when available, publication year, work type, citation count, and primary topic without requiring access to full text. The card SHALL group these into distinct visual lines rather than a single joined run of text: the title, then attribution with venue, then publication facts with the primary topic.

#### Scenario: Complete metadata
- **WHEN** a result contains every supported metadata field
- **THEN** the card presents those fields using the hierarchy represented by `docs/ui/lattice-search-reference.png`

#### Scenario: Metadata grouping
- **WHEN** a card renders attribution, venue, year, work type, citation count, and a primary topic
- **THEN** attribution and venue occupy one line, and year, work type, citation count, and the primary topic occupy a separate line, so that no single line carries all of them

#### Scenario: Title remains the anchor
- **WHEN** a card renders with the longest supported attribution and venue values
- **THEN** the title retains the greatest typographic emphasis on the card and the metadata lines do not visually outweigh it

#### Scenario: Optional metadata missing
- **WHEN** a result lacks a venue, citation count, or topic
- **THEN** the card omits the unavailable field without showing placeholder punctuation or breaking its layout

#### Scenario: A grouped line is entirely empty
- **WHEN** every field belonging to one grouped line is unavailable
- **THEN** the card omits that line entirely rather than rendering an empty row

### Requirement: Paper details
The application SHALL provide a paper-detail screen containing all available card metadata plus abstract and complete author information when supplied by the data source. Publication facts SHALL be presented as discrete labelled values rather than a single joined sentence, the author list SHALL be expandable when it exceeds the summary length, and the abstract SHALL be truncated with an explicit control to reveal the remainder.

#### Scenario: Open paper details
- **WHEN** a user selects a paper card
- **THEN** the system navigates to details for the selected stable paper ID

#### Scenario: Publication facts are discrete
- **WHEN** the detail screen renders venue, publication year, work type, and citation count
- **THEN** each is presented as a labelled value that can be read independently of the others

#### Scenario: Long author list
- **WHEN** a paper has more authors than the summary presentation shows
- **THEN** the screen indicates how many are hidden and offers a control that reveals the complete list

#### Scenario: Long abstract
- **WHEN** an abstract exceeds the truncation length
- **THEN** the screen truncates it and offers a control that reveals the remainder without leaving the screen

#### Scenario: Confirmed action stays reachable
- **WHEN** a paper has a confirmed access action and the user scrolls through a long abstract
- **THEN** the action remains reachable without scrolling back

#### Scenario: Detail refresh fails
- **WHEN** detail refresh fails but cached paper metadata exists
- **THEN** the system displays the cached details and recoverable refresh feedback
