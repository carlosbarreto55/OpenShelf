## ADDED Requirements

### Requirement: Cached result identity
For each normalized query, the search cache SHALL contain at most one ordered result association per stable paper ID and SHALL preserve the first occurrence's backend order across page appends and restarts.

#### Scenario: Overlapping page is persisted
- **WHEN** an appended page repeats an ID already associated with the query
- **THEN** the existing association retains its position and only previously unseen IDs receive later positions

#### Scenario: Page contains duplicate IDs
- **WHEN** a single incoming page contains the same valid paper ID multiple times
- **THEN** only the first occurrence is stored for that query

#### Scenario: Cache is reopened
- **WHEN** the app restarts after overlapping pages were processed
- **THEN** Room emits one paper per stable ID in the same first-seen order shown before restart

#### Scenario: Existing duplicate cache is migrated
- **WHEN** a database from the previous schema contains duplicate query/paper associations
- **THEN** migration keeps the lowest-position association and preserves paper rows, saved state, access snapshots, and download records

### Requirement: Cached search page metadata
The search cache SHALL persist the latest cursor and optional matched total for each normalized query without fabricating missing values.

#### Scenario: First page supplies a total
- **WHEN** a first page supplies a matched total
- **THEN** the query cache stores that total with the page cursor and emits it with observed results

#### Scenario: New first page omits a total
- **WHEN** a replacement first page omits the matched total
- **THEN** any total from the prior execution of that query is cleared rather than shown as current

#### Scenario: Append omits a known total
- **WHEN** an appended page omits the total but the current first page supplied one
- **THEN** the cache preserves the known current-query total while advancing the cursor

#### Scenario: Append supplies an updated total
- **WHEN** an appended page supplies a matched total
- **THEN** the cache stores and emits that supplied value without deriving a replacement from cached row count

#### Scenario: Cache has no supplied total
- **WHEN** no page for the current query supplied a total
- **THEN** observed cached results carry no total even after several pages are loaded
