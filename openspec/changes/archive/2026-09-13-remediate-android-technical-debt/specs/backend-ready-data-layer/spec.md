## ADDED Requirements

### Requirement: Canonical search request semantics
The HTTP client SHALL omit inactive optional restrictions, SHALL send canonical values for active enum-like filters, and SHALL preserve free-text topic input.

#### Scenario: Open-access filter is inactive
- **WHEN** `openAccessOnly` is false
- **THEN** the HTTP request omits the optional `open_access` restriction rather than relying on an upstream interpretation of false

#### Scenario: Open-access filter is active
- **WHEN** `openAccessOnly` is true
- **THEN** the HTTP request sends `open_access=true`

#### Scenario: Work type is selected
- **WHEN** the book-chapter filter is active
- **THEN** the request sends the canonical `book_chapter` value

#### Scenario: Topic text is selected
- **WHEN** a topic filter contains ordinary text
- **THEN** the request sends normalized free text and does not require or synthesize an OpenAlex topic ID

## MODIFIED Requirements

### Requirement: Replaceable remote source
The application SHALL depend on a remote data-source interface for search, detail, and download descriptors, and SHALL select fake or HTTP implementations through an explicit build configuration. An HTTP build MUST NOT silently fall back to fake behavior.

#### Scenario: Demo build starts without backend
- **WHEN** the configured remote mode is fake and no backend is reachable
- **THEN** the application remains fully navigable using deterministic fixture responses through the same repository interfaces used by the HTTP mode

#### Scenario: Integration build selects HTTP
- **WHEN** an integration build supplies a backend base URL and selects the HTTP binding
- **THEN** ViewModels and composables require no code changes to consume backend responses

#### Scenario: Integration configuration is invalid
- **WHEN** an integration build is missing a usable backend URL
- **THEN** it reports a configuration failure and never substitutes the fake source or simulated download gateway

### Requirement: Production-shaped placeholder fixtures
The fake remote source SHALL use the same DTOs, serializers, mappers, pagination fields, and transport-independent outcome model as the HTTP source. It MUST provide explicit cases for not-found, no-PDF, empty cursor pages, upstream unavailable, upstream timeout, and malformed data.

#### Scenario: Fixture is decoded
- **WHEN** a fixture search response is loaded
- **THEN** it passes through DTO deserialization, validation, domain mapping, repository persistence, and Room observation before reaching UI state

#### Scenario: Fixture models inaccessible paper
- **WHEN** a fixture contains no-free-full-text or unknown access
- **THEN** its mapped UI exposes no access affordance in accordance with the verified-access capability

#### Scenario: Fake edge case is selected
- **WHEN** a test selects a canonical failure or pagination fixture
- **THEN** the fake source produces the same repository-level result expected from the corresponding HTTP response

### Requirement: Normalized paper contract
The remote contract SHALL expose only product-required paper fields, SHALL identify papers using stable OpenAlex IDs plus an opaque cursor for pagination, and SHALL use canonical work-type values. The client SHALL defensively accept documented legacy aliases without emitting them.

#### Scenario: Search response contains extra backend fields
- **WHEN** the backend response adds an unknown optional field
- **THEN** compatible client deserialization continues without changing the domain model

#### Scenario: Required identity is missing
- **WHEN** a response item lacks a valid stable paper ID or title
- **THEN** the mapper rejects or safely omits that item and records a non-secret diagnostic

#### Scenario: Canonical book chapter is received
- **WHEN** a paper type is `book_chapter`
- **THEN** the client maps it to `PaperType.BOOK_CHAPTER`

#### Scenario: Legacy book chapter alias is received
- **WHEN** a paper type is the documented legacy value `book-chapter`, in any letter case
- **THEN** the client maps it to `PaperType.BOOK_CHAPTER` while future requests continue to emit `book_chapter`

#### Scenario: Work type is blank or unknown
- **WHEN** a paper type is blank/null or an unrecognized non-blank value
- **THEN** the client maps it to `UNKNOWN` or `OTHER` respectively without failing the paper

### Requirement: Predictable remote failures
The repository SHALL translate not-found, upstream unavailable, upstream timeout, connectivity, other HTTP, contract, and access-validation failures into typed domain outcomes without leaking secrets or raw server payloads into the UI. A malformed success body MUST remain distinct from not-found, and a valid no-PDF download response MUST remain distinct from both.

#### Scenario: Backend is unavailable
- **WHEN** the HTTP source cannot reach the backend and cached results exist
- **THEN** the repository emits cached content plus recoverable stale feedback

#### Scenario: Backend response violates contract
- **WHEN** a response cannot be safely mapped
- **THEN** the repository fails the affected operation, preserves valid cached data, and does not expose invalid access actions

#### Scenario: Paper is not found
- **WHEN** detail or download returns HTTP 404
- **THEN** the repository emits the first-class non-recoverable not-found outcome rather than a generic HTTP or contract failure

#### Scenario: Upstream is unavailable
- **WHEN** the backend returns HTTP 502
- **THEN** the repository emits a recoverable service-unavailable outcome

#### Scenario: Upstream times out
- **WHEN** the backend returns HTTP 504
- **THEN** the repository emits a recoverable timeout outcome distinct from not-found and malformed data

#### Scenario: Existing paper has no PDF
- **WHEN** the download endpoint returns HTTP 200 with the requested paper ID and a null descriptor
- **THEN** the repository emits a normal unavailable-descriptor outcome and does not classify the body as malformed
