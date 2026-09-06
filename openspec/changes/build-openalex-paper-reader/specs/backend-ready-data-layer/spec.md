## ADDED Requirements

### Requirement: Replaceable remote source
The application SHALL depend on a remote data-source interface for search, detail, access refresh, and download descriptors, and SHALL select fake or HTTP implementations through dependency injection or build configuration.

#### Scenario: Demo build starts without backend
- **WHEN** the configured remote mode is fake and no backend is reachable
- **THEN** the application remains fully navigable using deterministic fixture responses through the same repository interfaces used by the HTTP mode

#### Scenario: Integration build selects HTTP
- **WHEN** an integration build supplies a backend base URL and selects the HTTP binding
- **THEN** ViewModels and composables require no code changes to consume backend responses

### Requirement: Production-shaped placeholder fixtures
The fake remote source SHALL use the same DTOs, serializers, mappers, pagination fields, and error model as the future HTTP source.

#### Scenario: Fixture is decoded
- **WHEN** a fixture search response is loaded
- **THEN** it passes through DTO deserialization, validation, domain mapping, repository persistence, and Room observation before reaching UI state

#### Scenario: Fixture models inaccessible paper
- **WHEN** a fixture contains no-free-full-text or unknown access
- **THEN** its mapped UI exposes no access affordance in accordance with the verified-access capability

### Requirement: Normalized paper contract
The remote contract SHALL expose only product-required paper fields and SHALL identify papers using stable OpenAlex IDs plus an opaque cursor for pagination.

#### Scenario: Search response contains extra backend fields
- **WHEN** the backend response adds an unknown optional field
- **THEN** compatible client deserialization continues without changing the domain model

#### Scenario: Required identity is missing
- **WHEN** a response item lacks a valid stable paper ID or title
- **THEN** the mapper rejects or safely omits that item and records a non-secret diagnostic

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
The repository SHALL translate timeout, connectivity, HTTP, contract, and access-validation failures into typed domain outcomes without leaking secrets or raw server payloads into the UI.

#### Scenario: Backend is unavailable
- **WHEN** the HTTP source cannot reach the backend and cached results exist
- **THEN** the repository emits cached content plus recoverable stale feedback

#### Scenario: Backend response violates contract
- **WHEN** a response cannot be safely mapped
- **THEN** the repository fails the affected operation, preserves valid cached data, and does not expose invalid access actions
