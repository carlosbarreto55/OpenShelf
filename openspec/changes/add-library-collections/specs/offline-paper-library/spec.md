## ADDED Requirements

### Requirement: Local collections
The application SHALL allow users to create, rename and delete named collections locally without an account, and SHALL allow a saved paper to belong to any number of them.

#### Scenario: Create a collection
- **WHEN** a user creates a collection with a non-blank name
- **THEN** the system stores it locally and offers it wherever a paper can be assigned

#### Scenario: Blank or duplicate name
- **WHEN** a user attempts to create a collection with a blank name, or a name that already exists after normalisation
- **THEN** the system does not create it and keeps the user in the naming step

#### Scenario: Assign a saved paper
- **WHEN** a user assigns a saved paper to a collection
- **THEN** the paper appears under that collection while remaining in the full saved list

#### Scenario: Assign to several collections
- **WHEN** a user assigns one paper to more than one collection
- **THEN** the paper appears under each of them, stored once

#### Scenario: Remove from a collection
- **WHEN** a user removes a paper from a collection
- **THEN** the membership is removed and the paper remains saved and available in the full saved list

#### Scenario: Delete a collection
- **WHEN** a user deletes a collection
- **THEN** the system removes the collection and its memberships, and every paper that belonged to it remains saved

#### Scenario: Unsave a paper that belongs to collections
- **WHEN** a user unsaves a paper that belongs to one or more collections
- **THEN** the system removes it from every collection along with the saved association, and leaves any completed download record intact

#### Scenario: Collections survive restart
- **WHEN** the app restarts
- **THEN** collections and their memberships are restored exactly as they were, without connectivity

### Requirement: Library filtering and sorting
The application SHALL allow the saved list to be narrowed by a locally matched text filter and by facet, and SHALL allow it to be ordered by recently saved, publication year, citation count, or title. Filters and ordering SHALL combine.

#### Scenario: Text filter
- **WHEN** a user enters filter text
- **THEN** the system shows only saved papers whose title, an author name, or venue matches it, matched locally with no network request

#### Scenario: Filter by collection
- **WHEN** a user selects a collection
- **THEN** the system shows only the saved papers belonging to it

#### Scenario: Filter to unfiled papers
- **WHEN** a user selects the unfiled facet
- **THEN** the system shows only saved papers that belong to no collection

#### Scenario: Filter to downloaded papers
- **WHEN** a user selects the downloaded facet
- **THEN** the system shows only saved papers that have a completed download record, and this facet is derived solely from download records and never from a paper's access state

#### Scenario: Combined filter and sort
- **WHEN** a text filter, a facet, and a sort order are all active
- **THEN** the system applies the filters first and orders the surviving papers by the chosen order

#### Scenario: Ordering with missing values
- **WHEN** the list is ordered by publication year or citation count and some papers lack that value
- **THEN** papers lacking the value are ordered last, deterministically, rather than being hidden

#### Scenario: Filter matches nothing
- **WHEN** the active filters match no saved papers
- **THEN** the system presents an empty state that names the active filters and allows each to be cleared, without implying the library itself is empty

#### Scenario: Filter and sort are not destructive
- **WHEN** filters and ordering are active
- **THEN** no saved association, collection membership, or download record is modified

## MODIFIED Requirements

### Requirement: Cache retention boundaries
The application SHALL allow expirable search cache data to be pruned without deleting saved papers, collections, collection memberships, active downloads, or completed download history.

#### Scenario: Prune stale queries
- **WHEN** cache maintenance removes expired search queries and result associations
- **THEN** papers referenced by saved or download records remain available locally

#### Scenario: Prune does not touch collections
- **WHEN** cache maintenance runs
- **THEN** collections and their memberships are unaffected, and every paper referenced by a membership remains available locally
