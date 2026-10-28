# Live Backend Connectivity Specification

## Purpose
Reaching a real PaperReader server from the Android client: which build selects live HTTP transport, the network permission and cleartext rules that scope it, the local-development route to a server on the developer's own machine, and the verification that a real server — not only a simulated one — has been exercised.

## Requirements

### Requirement: Explicit backend build modes
The Android project SHALL fix its backend mode at build time through the product flavor. The demo
flavor SHALL select deterministic fake data without requiring a backend. The HTTP flavors
(`integration` and `production`) SHALL select the HTTP data and production download paths. No build
SHALL substitute fixture data when its backend is unreachable, and an unusable backend address MUST
fail the build rather than degrade it.

#### Scenario: Demo mode is built without a backend
- **WHEN** the demo variant is assembled without a backend URL
- **THEN** it builds successfully, selects the fake remote/download implementations, and remains usable without network service

#### Scenario: An HTTP flavor has valid configuration
- **WHEN** an HTTP flavor is built with a valid PaperReader backend base URL
- **THEN** it selects HTTP remote operations and the production download gateway without requiring UI or domain code changes

#### Scenario: Integration supplies no address
- **WHEN** the integration variant is assembled with no backend base URL supplied
- **THEN** it targets the deployed backend rather than failing, and never falls back to demo data

#### Scenario: Backend address is unusable
- **WHEN** an HTTP flavor is configured with an address that is not a usable `http(s)` URL
- **THEN** assembly fails with an actionable configuration message and no artifact is produced

### Requirement: Network permission is scoped to live connectivity
Every APK of an HTTP flavor MUST declare `android.permission.INTERNET`. Demo APKs SHALL remain functional without using the permission or issuing backend/PDF network requests.

#### Scenario: An HTTP merged manifest is inspected
- **WHEN** the merged manifest for an integration or production variant is verified
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
A release build of an HTTP flavor MUST accept only an HTTPS backend base URL and MUST NOT contain a cleartext network-security exception.

#### Scenario: Release URL is cleartext
- **WHEN** a release build is configured with an `http://` backend URL
- **THEN** configuration validation fails before the build is eligible for distribution

#### Scenario: Release manifest is inspected
- **WHEN** the merged manifest and packaged resources of a release build are checked
- **THEN** no debug cleartext override is present

### Requirement: Every backend mode is continuously verified
The repository SHALL provide documented local and CI commands that assemble and test every flavor. HTTP verification SHALL include a controlled network search and PDF transfer without embedding an OpenAlex credential.

#### Scenario: Pull request verification runs
- **WHEN** Android CI evaluates a change to build, remote, persistence, or download code
- **THEN** it assembles every flavor and runs the contract-backed HTTP checks in addition to demo/JVM tests

#### Scenario: Packaged clients are scanned
- **WHEN** demo or HTTP-flavor artifacts are produced
- **THEN** the existing secret checks confirm that no artifact contains an OpenAlex host credential or API key

### Requirement: A real server is exercised, not only a simulated one
HTTP verification SHALL include a suite that opens a connection to the configured PaperReader backend itself and drives it through the production remote source and mappers. That suite MUST skip rather than fail when no server is listening, and MUST derive every result identity from a search performed in the same run rather than hardcoding upstream identifiers.

#### Scenario: A backend is reachable
- **WHEN** an HTTP flavor runs its live suite against a running PaperReader server
- **THEN** search, cursor advancement, paper detail, download confirmation, the no-PDF outcome, and the unknown-paper outcome are each verified end to end over real HTTP

#### Scenario: No backend is running
- **WHEN** the live suite runs on a machine with no PaperReader server listening at the configured address
- **THEN** its cases are skipped and the build stays green, because an absent development backend is not a defect in the client

#### Scenario: Upstream results change between runs
- **WHEN** the live suite needs a paper with a particular access state
- **THEN** it selects one from the results of its own search and skips the case when the live page contains none, so real upstream churn never turns into a false failure
