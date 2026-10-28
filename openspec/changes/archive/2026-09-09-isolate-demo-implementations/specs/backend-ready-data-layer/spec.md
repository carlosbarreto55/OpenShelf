## MODIFIED Requirements

### Requirement: Replaceable remote source
The application SHALL depend on a remote data-source interface for search, detail, and download
descriptors, and SHALL select the fake or HTTP implementation **at compile time, through the product
flavor's own dependency-injection module**. The implementation a build does not select MUST NOT be
present on that build's classpath. Selection MUST NOT be a runtime branch over implementations that
are both packaged.

#### Scenario: Demo build starts without backend
- **WHEN** the demo flavor is built and no backend is reachable
- **THEN** the application remains fully navigable using deterministic fixture responses through the same repository interfaces the HTTP mode uses

#### Scenario: Integration build selects HTTP
- **WHEN** an integration build supplies a backend base URL
- **THEN** ViewModels and composables require no code changes to consume backend responses

#### Scenario: Integration artifact is inspected for demo implementations
- **WHEN** a packaged integration APK is scanned for the fake remote source, the fake download gateway, or the fixture catalog
- **THEN** none of them is present, and a test asserts those classes cannot be loaded in an integration build at all

#### Scenario: A demo implementation is reintroduced into shared code
- **WHEN** a fake implementation is moved or copied back into the shared source set
- **THEN** the per-flavor selection test fails, because the class becomes loadable in an integration build

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
