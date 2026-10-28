# Backend-Ready Data Layer Specification

## Purpose
A data layer shaped for a future PaperReader backend: a replaceable remote source, production-shaped placeholder fixtures, normalized paper and access contracts, a secret-free Android client, and predictable remote failures.

## Requirements

### Requirement: Replaceable remote source
The application SHALL depend on a remote data-source interface for search, detail, and download
descriptors, and SHALL select the fake or HTTP implementation **at compile time, through the product
flavor's own dependency-injection module**. The implementation a build does not select MUST NOT be
present on that build's classpath. Selection MUST NOT be a runtime branch over implementations that
are both packaged, and no build SHALL substitute fixture data when its configured backend is missing
or unusable.

#### Scenario: Demo build starts without backend
- **WHEN** the demo flavor is built and no backend is reachable
- **THEN** the application remains fully navigable using deterministic fixture responses through the same repository interfaces the HTTP mode uses

#### Scenario: Integration build selects HTTP
- **WHEN** an integration build supplies a backend base URL
- **THEN** ViewModels and composables require no code changes to consume backend responses

#### Scenario: Integration build supplies no address
- **WHEN** an integration build is assembled with no backend base URL supplied
- **THEN** it targets the deployed backend rather than failing, and never falls back to fixture data

#### Scenario: HTTP builds share one binding
- **WHEN** any build that selects the HTTP binding is assembled
- **THEN** it compiles the same remote source, download gateway, and Internet permission declaration
  as every other HTTP build, and no fixture implementation is present on its classpath

#### Scenario: Integration artifact is inspected for demo implementations
- **WHEN** a packaged integration APK is scanned for the fake remote source, the fake download gateway, or the fixture catalog
- **THEN** none of them is present, and a test asserts those classes cannot be loaded in an integration build at all

#### Scenario: A demo implementation is reintroduced into shared code
- **WHEN** a fake implementation is moved or copied back into the shared source set
- **THEN** the per-flavor selection test fails, because the class becomes loadable in an integration build

### Requirement: Deployed production build target
The application SHALL provide a build target whose PaperReader backend address is fixed at build
time to the deployed service, takes no external input, and never permits cleartext transport in any
build type.

#### Scenario: Production build takes no address input
- **WHEN** the production build is assembled with no backend-address property supplied, or with one
  supplied that names a different address
- **THEN** the build carries the deployed HTTPS base URL regardless, and the supplied property has
  no effect on it

#### Scenario: Production build permits no cleartext
- **WHEN** the merged manifest and packaged resources of any production build type are inspected
- **THEN** they declare the Internet permission and carry no cleartext-traffic attribute and no
  network-security configuration, including in the debuggable build type

#### Scenario: Production build is signed for release
- **WHEN** a production release artifact is assembled without release signing material configured
- **THEN** assembly fails with an actionable message rather than producing an unsigned artifact

#### Scenario: Production build shows no placeholder notice
- **WHEN** the production build runs
- **THEN** it resolves to the HTTP remote source with no fixture implementation on its classpath and
  displays no placeholder-data notice

### Requirement: Production-shaped placeholder fixtures
The fake remote source SHALL use the same DTOs, serializers, mappers, pagination fields, and error
model as the HTTP source. It SHALL remain available to the test suites that need deterministic
failure injection, since a healthy backend cannot produce those outcomes on demand.

#### Scenario: Fixture is decoded
- **WHEN** a fixture search response is loaded
- **THEN** it passes through DTO deserialization, validation, domain mapping, repository persistence, and Room observation before reaching UI state

#### Scenario: Fixture models inaccessible paper
- **WHEN** a fixture contains no-free-full-text or unknown access
- **THEN** its mapped UI exposes no access affordance in accordance with the verified-access capability

#### Scenario: A failure outcome must be reproduced deterministically
- **WHEN** a test needs not-found, no-PDF, an empty page that still carries a cursor, an unavailable or timed-out upstream, mismatched identity, or malformed data
- **THEN** the fake source injects that outcome directly, and the parity suite asserts it matches what the HTTP source produces from the equivalent status

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

### Requirement: Typed access contract
The remote contract SHALL preserve backend access states explicitly and SHALL validate action descriptors before mapping them to actionable domain values.

#### Scenario: Unknown future access value
- **WHEN** the backend returns an unrecognized access-state value
- **THEN** the client maps it to a non-actionable unknown state rather than enabling access

#### Scenario: Generated PDF is confirmed
- **WHEN** the future backend confirms a generated PDF created from freely reachable content and supplies valid provenance and a download descriptor
- **THEN** the client maps it to an actionable confirmed PDF while retaining its generated provenance

### Requirement: Secret-free Android client
The Android application MUST NOT contain, log, or transmit an OpenAlex API key and MUST NOT call authenticated OpenAlex endpoints directly in the production data path.

#### Scenario: Build artifacts are inspected
- **WHEN** source and packaged Android resources are scanned for the supplied OpenAlex credential
- **THEN** the credential is absent from source, generated configuration, logs, fixtures, and APK contents

#### Scenario: Live search is enabled later
- **WHEN** the HTTP remote source is activated
- **THEN** search requests target the configured PaperReader backend rather than OpenAlex directly

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
