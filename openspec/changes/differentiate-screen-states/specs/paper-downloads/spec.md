## ADDED Requirements

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
