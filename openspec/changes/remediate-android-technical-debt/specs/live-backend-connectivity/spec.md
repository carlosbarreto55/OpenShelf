## ADDED Requirements

### Requirement: Explicit backend build modes
The Android project SHALL provide separately buildable demo and integration modes. Demo mode SHALL select deterministic fake data without requiring a backend, while integration mode SHALL select the HTTP data and production download paths and MUST fail explicitly when its backend configuration is missing or invalid.

#### Scenario: Demo mode is built without a backend
- **WHEN** the demo variant is assembled without a backend URL
- **THEN** it builds successfully, selects the fake remote/download implementations, and remains usable without network service

#### Scenario: Integration mode has valid configuration
- **WHEN** the integration variant is built with a valid PaperReader backend base URL
- **THEN** it selects HTTP remote operations and the production download gateway without requiring UI or domain code changes

#### Scenario: Integration mode is incomplete
- **WHEN** the integration variant has no backend URL or an invalid URL
- **THEN** the build or application startup fails with a configuration error and does not fall back to demo data

### Requirement: Network permission is scoped to live connectivity
Every integration APK MUST declare `android.permission.INTERNET`. Demo APKs SHALL remain functional without using the permission or issuing backend/PDF network requests.

#### Scenario: Integration merged manifest is inspected
- **WHEN** the merged manifest for an integration variant is verified
- **THEN** it contains the Internet permission needed by Retrofit and external PDF transfers

#### Scenario: Demo flow is exercised
- **WHEN** a demo variant runs with networking unavailable
- **THEN** search, detail, library, and simulated downloads remain functional through fake implementations

### Requirement: Debug-only local HTTP support
The project SHALL provide a documented local-development route from an emulator or ADB-reversed device to a PaperReader server. Any cleartext exception MUST be restricted to the integration-debug source set and named loopback development hosts.

#### Scenario: Emulator uses the local API
- **WHEN** an integration-debug build targets the documented emulator loopback HTTP host
- **THEN** search and a controlled download request can reach the local server

#### Scenario: Unlisted cleartext host is used
- **WHEN** integration-debug attempts cleartext traffic to a host outside the debug allowlist
- **THEN** Android network security rejects the connection

### Requirement: Release transport remains HTTPS-only
An integration-release build MUST accept only an HTTPS backend base URL and MUST NOT contain a cleartext network-security exception.

#### Scenario: Release URL is cleartext
- **WHEN** an integration-release build is configured with an `http://` backend URL
- **THEN** configuration validation fails before the build is eligible for distribution

#### Scenario: Release manifest is inspected
- **WHEN** the integration-release merged manifest and packaged resources are checked
- **THEN** no debug cleartext override is present

### Requirement: Both backend modes are continuously verified
The repository SHALL provide documented local and CI commands that assemble and test demo and integration variants. Integration verification SHALL include a controlled network search and PDF transfer without embedding an OpenAlex credential.

#### Scenario: Pull request verification runs
- **WHEN** Android CI evaluates a change to build, remote, persistence, or download code
- **THEN** it assembles both modes and runs the contract-backed integration checks in addition to demo/JVM tests

#### Scenario: Packaged clients are scanned
- **WHEN** demo or integration artifacts are produced
- **THEN** the existing secret checks confirm that neither artifact contains an OpenAlex host credential or API key
