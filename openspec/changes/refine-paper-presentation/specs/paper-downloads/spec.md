## MODIFIED Requirements

### Requirement: Observable download lifecycle
The application SHALL persist and display queued, running, completed, failed, and canceled download states with determinate progress when byte counts are known. Every surface that presents a paper SHALL present that paper's current download state in the position its access action occupies, and SHALL offer cancel while the transfer is queued or running.

#### Scenario: Download progresses
- **WHEN** bytes are being transferred for a paper
- **THEN** the Downloads screen and relevant paper UI observe the same current progress state

#### Scenario: Download starts from a paper surface
- **WHEN** a user starts a download from a search card, a library card, or the paper-detail screen
- **THEN** that surface immediately reflects the queued state in place, without the user navigating to the Downloads screen to confirm that anything happened

#### Scenario: Cancel from a paper surface
- **WHEN** a paper surface is presenting a queued or running download and the user activates cancel there
- **THEN** the system cancels that transfer and the surface reflects the canceled state

#### Scenario: Same paper on two surfaces
- **WHEN** the same paper is visible in the Library and on the Downloads screen while its transfer is running
- **THEN** both present the same state and progress, because both observe the same persisted record

#### Scenario: Paper has no download record
- **WHEN** a paper has a confirmed access action and no download record exists for it
- **THEN** the surface presents the access action as normal, with no download state

#### Scenario: Download state never implies access
- **WHEN** a paper's access state does not yield a confirmed action
- **THEN** no download state, progress, or download control is presented for that paper on any surface

#### Scenario: App restarts during download
- **WHEN** the app process restarts while a production download is queued or running
- **THEN** the system restores the persisted record and reconciles it with the background worker state
