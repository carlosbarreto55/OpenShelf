## ADDED Requirements

### Requirement: Reported result totals
The search response contract SHALL carry an optional count of results matching the query. The client MUST treat the field as absent-safe and MUST NOT derive or estimate a total when the data source supplies none.

#### Scenario: Backend supplies a total
- **WHEN** a search response includes a matched-result total
- **THEN** the client carries it through mapping and persistence to the discovery UI unchanged

#### Scenario: Backend omits the total
- **WHEN** a search response omits the field
- **THEN** deserialization succeeds, the domain page carries no total, and no total is displayed

#### Scenario: Existing fixtures are unchanged
- **WHEN** a fixture written before this field existed is decoded
- **THEN** it decodes successfully and reports no total

#### Scenario: Total is not fabricated
- **WHEN** no total has been supplied for a query
- **THEN** the client does not substitute the number of loaded results, the page size, or any other value in its place

#### Scenario: Cached results report their stored total
- **WHEN** cached results are displayed for a query whose stored page recorded a total
- **THEN** the system reports that stored total alongside the cached results
