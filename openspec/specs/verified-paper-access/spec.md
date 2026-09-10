# Verified Paper Access Specification

## Purpose
Fail-closed rendering of paper access: an access action is exposed only for a backend-confirmed, validated access state, and every inaccessible or uncertain state renders silently with no badge, URL, lock, or action.

## Requirements

### Requirement: Confirmed PDF action
The application SHALL display a Download PDF action only when the normalized access state is `DIRECT_PDF` and contains a valid backend-confirmed download descriptor.

#### Scenario: Direct PDF confirmed
- **WHEN** a paper has `DIRECT_PDF` access with a valid descriptor
- **THEN** the paper UI displays a Download PDF action

#### Scenario: Descriptor provenance is optional
- **WHEN** a confirmed descriptor carries provenance text
- **THEN** the paper UI MAY display it, and MUST NOT be considered non-compliant for omitting it

#### Scenario: PDF descriptor is invalid
- **WHEN** a paper is marked `DIRECT_PDF` but its download descriptor is missing or invalid
- **THEN** the system renders the paper as metadata-only and exposes no access action

### Requirement: Confirmed open-full-text action
The application SHALL display a Read online action only when the normalized access state is `OPEN_FULL_TEXT` and contains a valid HTTPS source URL.

#### Scenario: Open HTML source confirmed
- **WHEN** a paper has `OPEN_FULL_TEXT` access with a valid HTTPS source URL
- **THEN** the paper UI displays a Read online action

#### Scenario: Source provenance is optional
- **WHEN** a confirmed open-full-text resolution carries provenance text or a source host
- **THEN** the paper UI MAY display it, and MUST NOT be considered non-compliant for omitting it

#### Scenario: Open source URL is invalid
- **WHEN** an open-full-text response contains a missing, malformed, or non-HTTPS URL
- **THEN** the system exposes no source row or Read online action

### Requirement: Silent inaccessible and uncertain states
The application MUST render `NO_FREE_FULL_TEXT`, `PAYWALLED`, `UNKNOWN`, `CHECKING`, and access-resolution error states without an access badge, source URL, lock icon, status label, or access action.

#### Scenario: No free full text exists
- **WHEN** a paper's access state is `NO_FREE_FULL_TEXT`
- **THEN** the paper remains discoverable by metadata but contains no access-related UI

#### Scenario: Verification is inconclusive
- **WHEN** a paper's access state is `UNKNOWN` or an access check fails
- **THEN** the paper remains discoverable by metadata but contains no access-related UI

#### Scenario: Source is paywalled
- **WHEN** a paper's access state is `PAYWALLED`
- **THEN** the paper remains discoverable by metadata but contains no lock, paywall flag, source row, URL, or action

### Requirement: Access state applies consistently
The system SHALL apply the same access-to-UI mapping on search cards, paper details, saved papers, and download entry points.

#### Scenario: Inaccessible paper is saved
- **WHEN** a saved paper has an inaccessible or uncertain access state
- **THEN** the library and detail views expose no access affordance for that paper

#### Scenario: Confirmed access changes
- **WHEN** a newer normalized response changes a paper from confirmed access to an inaccessible or uncertain state
- **THEN** the system removes the stale access affordance while retaining bibliographic and saved-paper data

### Requirement: Backend-owned access verification
The Android application MUST consume normalized access decisions and MUST NOT scrape source pages, infer access from a publisher URL, or treat OpenAlex's preferred location as proof of PDF availability.

#### Scenario: Metadata contains a publisher landing page
- **WHEN** paper metadata includes a publisher URL without a confirmed normalized access state
- **THEN** the system does not expose that URL as an access action

#### Scenario: Backend later checks multiple locations
- **WHEN** the future backend returns a confirmed repository PDF after evaluating multiple candidate locations
- **THEN** the client displays the action solely from the normalized confirmed result
