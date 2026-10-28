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

### Requirement: Transfer-time descriptor confirmation
Immediately before every initial or retried production transfer, the application MUST request the paper's download descriptor from the backend and MUST enqueue work only from the newly returned, matching, unexpired, valid HTTPS descriptor. A cached descriptor embedded in a paper or download record MUST NOT by itself authorize a transfer.

#### Scenario: Initial confirmation succeeds
- **WHEN** a user starts a download from a current confirmed PDF action and the download endpoint returns a valid descriptor for the requested paper
- **THEN** the gateway enqueues the transfer using that newly returned descriptor

#### Scenario: Retry confirmation succeeds with a changed URL
- **WHEN** a failed download is retried and the endpoint returns a different valid descriptor URL
- **THEN** the persisted descriptor is replaced and the retry uses the newly returned URL rather than the previously stored one

#### Scenario: Existing paper has no confirmed PDF
- **WHEN** the endpoint returns HTTP 200 with the requested paper ID and a null descriptor
- **THEN** the application reports that no PDF is currently available, enqueues no work, and does not classify the response as malformed

#### Scenario: Paper is not found
- **WHEN** the endpoint returns HTTP 404
- **THEN** the application presents the stable not-found outcome and enqueues no work

#### Scenario: Response identity does not match
- **WHEN** the endpoint returns a descriptor for a paper ID other than the requested ID
- **THEN** the application reports a contract failure and enqueues no work

#### Scenario: Descriptor is malformed or expired
- **WHEN** the newly returned descriptor has no valid HTTPS URL or has already expired
- **THEN** the application fails closed with an access-validation outcome and enqueues no work

#### Scenario: Confirmation times out or is offline
- **WHEN** descriptor confirmation fails through HTTP 504, socket timeout, or unavailable connectivity
- **THEN** the application reports a recoverable failure, retains the existing download record state, and does not fall back to the cached URL

#### Scenario: Upstream is unavailable
- **WHEN** descriptor confirmation returns HTTP 502
- **THEN** the application reports a recoverable service failure and enqueues no work

#### Scenario: Demo transfer is confirmed
- **WHEN** demo mode starts or retries a simulated download
- **THEN** the fake descriptor operation participates in the same repository flow before the fake gateway simulates transfer lifecycle

### Requirement: Observable download lifecycle
The application SHALL persist and display queued, running, completed, failed, and canceled download states with determinate progress when byte counts are known.

#### Scenario: Download progresses
- **WHEN** bytes are being transferred for a paper
- **THEN** the Downloads screen and relevant paper UI observe the same current progress state

#### Scenario: App restarts during download
- **WHEN** the app process restarts while a production download is queued or running
- **THEN** the system restores the persisted record and reconciles it with the background worker state

### Requirement: Download list organisation
The Downloads screen SHALL group download records by what the user must do with them — transfers in progress, records needing a decision, and completed files — rather than presenting one undifferentiated list. Failed records SHALL state the cause of failure.

#### Scenario: Records in several states
- **WHEN** the Downloads screen shows a running transfer, a failed record, and a completed record at the same time
- **THEN** each appears under a distinct group, and the groups are ordered so that in-progress and failed records precede completed ones

#### Scenario: Failure names its cause
- **WHEN** a download failed because the response was not a valid PDF
- **THEN** the record states that cause in user-facing terms and states that no partial file was kept

#### Scenario: Only one group has records
- **WHEN** every record is in the same state
- **THEN** the screen presents that group without rendering empty headings for the others

#### Scenario: Clear completed
- **WHEN** the user clears completed downloads
- **THEN** the system removes the completed records and their managed local files, and leaves in-progress, failed, and saved-paper records intact

#### Scenario: No downloads at all
- **WHEN** no download records exist
- **THEN** the system presents the empty state without group headings

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
