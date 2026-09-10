## MODIFIED Requirements

### Requirement: Open a completed local PDF
The application SHALL open a validated completed PDF in its in-app reader by default, and SHALL retain handing the file to an external application through a content URI as an explicit alternative, without exposing unrestricted filesystem paths.

#### Scenario: Open downloaded paper
- **WHEN** a user activates Open on a completed download
- **THEN** the system presents the paper in the in-app reader

#### Scenario: Open in another application
- **WHEN** a user explicitly chooses to open a completed download in another application and a compatible viewer is installed
- **THEN** the system launches the viewer with temporary read permission for the paper content URI

#### Scenario: In-app reader cannot render the file
- **WHEN** the in-app reader cannot render a completed download
- **THEN** the system reports why, keeps the download record, and offers the external handoff as an alternative

#### Scenario: No viewer is available
- **WHEN** the user chooses the external handoff and no installed application can open the PDF
- **THEN** the system keeps the download record and displays actionable error feedback

#### Scenario: Filesystem paths stay private
- **WHEN** a completed file is opened by either route
- **THEN** no unrestricted filesystem path is exposed to another application
