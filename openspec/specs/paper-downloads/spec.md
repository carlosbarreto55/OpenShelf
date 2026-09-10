# Paper Downloads Specification

## Purpose
Downloading confirmed-access PDFs: starting downloads only from verified access, an observable and restart-safe lifecycle, retry and cancel, validation of completed files, opening local PDFs, and honest placeholder behavior until a real backend exists.

## Requirements

### Requirement: Start only verified PDF downloads
The application SHALL allow a download to start only from a current `DIRECT_PDF` state with a valid backend-confirmed descriptor.

#### Scenario: User starts confirmed download
- **WHEN** a user activates Download PDF for a currently confirmed PDF
- **THEN** the system creates or resumes a persisted queued download record for that paper

#### Scenario: Access is no longer confirmed
- **WHEN** a download is requested after the paper's access descriptor has become invalid or expired
- **THEN** the system does not start the transfer and exposes no fallback publisher download

### Requirement: Observable download lifecycle
The application SHALL persist and display queued, running, completed, failed, and canceled download states with determinate progress when byte counts are known.

#### Scenario: Download progresses
- **WHEN** bytes are being transferred for a paper
- **THEN** the Downloads screen and relevant paper UI observe the same current progress state

#### Scenario: App restarts during download
- **WHEN** the app process restarts while a production download is queued or running
- **THEN** the system restores the persisted record and reconciles it with the background worker state

### Requirement: Retry and cancel downloads
The application SHALL allow users to retry failed downloads and cancel queued or running downloads.

#### Scenario: Retry failure
- **WHEN** a user retries a failed download and the descriptor remains valid
- **THEN** the system requeues the transfer and clears the prior transient error

#### Scenario: Cancel running download
- **WHEN** a user cancels a running download
- **THEN** the system stops the work, removes any incomplete file, and persists the canceled state

### Requirement: Validate completed PDFs
Before marking a production download complete, the application SHALL verify that the response is a non-empty PDF using response metadata and file signature checks.

#### Scenario: Server returns HTML instead of PDF
- **WHEN** a download endpoint responds with an HTML error or landing page
- **THEN** the system marks the download failed and does not expose the file as a completed PDF

#### Scenario: Valid PDF completes
- **WHEN** the transfer finishes and PDF validation succeeds
- **THEN** the system stores the managed local file reference and marks the record completed

### Requirement: Open a completed local PDF
The application SHALL allow a user to open a validated completed PDF through a content URI without exposing unrestricted filesystem paths.

#### Scenario: Open downloaded paper
- **WHEN** a user activates Open on a completed download and a compatible viewer is installed
- **THEN** the system launches the viewer with temporary read permission for the paper content URI

#### Scenario: No viewer is available
- **WHEN** no installed application can open the PDF
- **THEN** the system keeps the download record and displays actionable error feedback

### Requirement: Honest placeholder download behavior
While no backend exists, the fake download implementation MUST simulate lifecycle states without creating a fabricated document that could be mistaken for the requested paper.

#### Scenario: Demo download reaches its terminal state
- **WHEN** a user runs the placeholder download flow in a demo build
- **THEN** the system visibly identifies the flow as demo behavior and does not present generated placeholder bytes as the real paper
