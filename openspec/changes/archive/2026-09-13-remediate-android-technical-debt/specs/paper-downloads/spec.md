## ADDED Requirements

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
