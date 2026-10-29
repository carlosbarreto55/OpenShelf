## MODIFIED Requirements

### Requirement: Confirmed PDF action
The application SHALL display a Read PDF action only when the normalized access state is `DIRECT_PDF` and contains a valid backend-confirmed download descriptor.

#### Scenario: Direct PDF confirmed
- **WHEN** a paper has `DIRECT_PDF` access with a valid descriptor
- **THEN** the paper UI displays a Read PDF action

#### Scenario: Descriptor provenance is optional
- **WHEN** a confirmed descriptor carries provenance text
- **THEN** the paper UI MAY display it, and MUST NOT be considered non-compliant for omitting it

#### Scenario: PDF descriptor is invalid
- **WHEN** a paper is marked `DIRECT_PDF` but its download descriptor is missing or invalid
- **THEN** the system renders the paper as metadata-only and exposes no access action

#### Scenario: Local copy of a paper that lost confirmed access
- **WHEN** a paper has a completed download but its current access state is no longer a confirmed `DIRECT_PDF`
- **THEN** the paper UI exposes no Read PDF action, and the local copy remains reachable only from the downloads list and the continue-reading entry point
