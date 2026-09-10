## ADDED Requirements

### Requirement: Versioned consumer contract bundle
The Android repository SHALL test against a pinned version of the canonical PaperReader API contract bundle. The pinned bundle MUST identify its API version and upstream checksum so that a local edit cannot silently redefine server behavior.

#### Scenario: Canonical v1 bundle is updated
- **WHEN** the pinned v1 contract fixtures or schema change
- **THEN** Android verification requires a reviewed contract-version/checksum update and reruns every affected consumer scenario

#### Scenario: Pinned content drifts from its manifest
- **WHEN** contract resources do not match the recorded checksum
- **THEN** the consumer-contract check fails before Android code is merged

### Requirement: Transport and semantic consumer verification
Android consumer tests SHALL exercise the real Retrofit serialization and HTTP status path and SHALL assert request meaning and domain outcomes, not only JSON field presence.

#### Scenario: Default search is encoded
- **WHEN** the client searches with no open-access restriction
- **THEN** the HTTP request does not express an `is_oa:false` restriction and the response is decoded through the production serializer and mapper

#### Scenario: Free-text topic is encoded
- **WHEN** the client searches with topic `education`
- **THEN** the HTTP request preserves that free text and does not transform it into a topic identifier

#### Scenario: Error matrix is evaluated
- **WHEN** fixtures return 404, 502, 504, malformed success JSON, mismatched identity, or a valid no-PDF response
- **THEN** each case maps to its specified distinct domain outcome without exposing the raw response body

#### Scenario: Pagination and value matrix is evaluated
- **WHEN** fixtures contain empty cursor pages, overlapping IDs, totals present or absent, canonical work types, aliases, or unknown enum values
- **THEN** the client preserves the specified pagination, identity, total, and fallback semantics

### Requirement: Fake and HTTP semantic parity
The fake and HTTP remote modes SHALL satisfy one reusable repository-level scenario matrix for transport-independent outcomes.

#### Scenario: Canonical outcome is replayed in both modes
- **WHEN** the parity suite runs a success, empty, not-found, no-PDF, timeout, unavailable, or malformed-data case
- **THEN** both modes yield the same repository-level outcome and persistence behavior

#### Scenario: Transport-only mechanism differs
- **WHEN** HTTP uses a status code while fake mode injects the equivalent outcome directly
- **THEN** the difference remains below the repository boundary and ViewModels receive equivalent state

### Requirement: Backward-compatible v1 rollout
Changes within API v1 MUST remain compatible with supported installed Android clients. Existing field meanings and nullability MUST NOT become incompatible, removals MUST wait for the documented support window, and breaking behavior MUST use a new API version.

#### Scenario: Optional response field is added
- **WHEN** the v1 server adds an optional field
- **THEN** supported Android clients continue decoding the response and may ignore the field

#### Scenario: Breaking contract change is proposed
- **WHEN** a path, required field, nullability rule, query meaning, or existing enum meaning would change incompatibly
- **THEN** the change is assigned a new API version and v1 remains available for the documented mobile support window

#### Scenario: Unknown access state reaches an older client
- **WHEN** a supported client receives a newly added access-state value it does not recognize
- **THEN** the client keeps the paper non-actionable according to the fail-closed fallback

### Requirement: Shared contract closure is provider-backed
Android SHALL keep shared contract debt open until provider verification consumes the same canonical version and covers the agreed response and failure policies.

#### Scenario: Only Android consumer tests pass
- **WHEN** the Android contract and parity suites pass but provider CI has not verified the same bundle
- **THEN** Android may proceed in demo/testing modes but shared contract debt is not marked complete and live release remains gated

#### Scenario: Consumer and provider suites pass
- **WHEN** both repositories verify the same versioned contract bundle and the coordinated rollout checks pass
- **THEN** the corresponding shared compatibility gate may be closed
