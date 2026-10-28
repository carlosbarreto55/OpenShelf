## ADDED Requirements

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

## MODIFIED Requirements

### Requirement: Replaceable remote source
The application SHALL depend on a remote data-source interface for search, detail, access refresh, and download descriptors, and SHALL select fake or HTTP implementations through dependency injection or build configuration. Every build SHALL fix that selection at build time, and no build SHALL substitute fixture data when its configured backend is missing or unusable.

#### Scenario: Demo build starts without backend
- **WHEN** the configured remote mode is fake and no backend is reachable
- **THEN** the application remains fully navigable using deterministic fixture responses through the same repository interfaces used by the HTTP mode

#### Scenario: Integration build selects HTTP
- **WHEN** an integration build supplies a backend base URL and selects the HTTP binding
- **THEN** ViewModels and composables require no code changes to consume backend responses

#### Scenario: Integration build supplies no address
- **WHEN** an integration build is assembled with no backend base URL supplied
- **THEN** it targets the deployed backend rather than failing, and never falls back to fixture data

#### Scenario: HTTP builds share one binding
- **WHEN** any build that selects the HTTP binding is assembled
- **THEN** it compiles the same remote source, download gateway, and Internet permission declaration
  as every other HTTP build, and no fixture implementation is present on its classpath
