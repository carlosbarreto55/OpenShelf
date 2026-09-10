# Offline Paper Library Specification

## Purpose
Local persistence that makes the app usable offline: cached search results, recent searches, a saved-paper library, cache retention boundaries, and freshness rules for cached access snapshots.

## Requirements

### Requirement: Cached search results
The application SHALL persist normalized paper metadata and ordered query results in Room and SHALL read observable result state from the local database.

#### Scenario: Successful search is cached
- **WHEN** a search page is received successfully
- **THEN** the system transactionally stores paper metadata and its ordered association with the normalized query

#### Scenario: App restarts offline
- **WHEN** the app restarts without connectivity after a previous successful search
- **THEN** the system can display the cached results for that query with offline or stale context

### Requirement: Recent searches
The application SHALL store a bounded, deduplicated list of recent submitted queries locally without requiring an account.

#### Scenario: Repeat query
- **WHEN** a user submits a query that already exists in recent searches
- **THEN** the system moves the normalized query to the most recent position without creating a duplicate

#### Scenario: Clear recents
- **WHEN** the user clears recent searches
- **THEN** the system removes recent-query records without removing saved papers or completed downloads

### Requirement: Saved paper library
The application SHALL allow users to save and unsave papers locally and SHALL preserve saved state across searches, app restarts, and cache refreshes.

#### Scenario: Save a result
- **WHEN** a user activates Save on a paper card
- **THEN** the paper appears in the local Library and its saved state updates wherever that paper is shown

#### Scenario: Unsave a paper
- **WHEN** a user removes a paper from the Library
- **THEN** the saved association is removed while any independent completed download record remains intact

### Requirement: Cache retention boundaries
The application SHALL allow expirable search cache data to be pruned without deleting saved papers, active downloads, or completed download history.

#### Scenario: Prune stale queries
- **WHEN** cache maintenance removes expired search queries and result associations
- **THEN** papers referenced by saved or download records remain available locally

### Requirement: Access snapshot freshness
The application SHALL persist the latest normalized access state with its verification timestamp and SHALL treat expired access data conservatively.

#### Scenario: Cached confirmed access expires
- **WHEN** a confirmed access snapshot is older than the configured freshness threshold and cannot be refreshed
- **THEN** the system suppresses the action or marks it non-actionable according to repository policy rather than assuming continued availability

#### Scenario: Metadata refresh succeeds without access data
- **WHEN** bibliographic metadata refreshes but no valid access resolution is supplied
- **THEN** the system does not retain a previously actionable descriptor as current proof of access
