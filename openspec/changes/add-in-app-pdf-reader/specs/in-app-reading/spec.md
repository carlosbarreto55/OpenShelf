## ADDED Requirements

### Requirement: Open a paper in the reader
The application SHALL open a paper in an in-app reader only from a completed download whose managed local file exists and passes PDF validation. The reader MUST NOT be reachable for any other paper.

#### Scenario: Open a completed download
- **WHEN** a user opens a completed, validated download
- **THEN** the system presents the paper in the in-app reader without leaving the application

#### Scenario: Paper has no completed download
- **WHEN** a paper has no completed download record
- **THEN** no route into the reader is presented for it

#### Scenario: Managed file is missing
- **WHEN** a completed record's managed local file no longer exists on disk
- **THEN** the system does not open the reader, reports that the file is no longer available, and offers to download it again if the access descriptor is still valid

### Requirement: Read a paper
The reader SHALL present the document's pages in order, SHALL allow the user to move between pages and to zoom, and SHALL report the current page against the total.

#### Scenario: Multi-page document
- **WHEN** a document with several pages is opened
- **THEN** the reader presents them in document order and reports the current page number and the total

#### Scenario: Zoom
- **WHEN** the user zooms into a page
- **THEN** the page re-renders at a legible resolution for that zoom level rather than being scaled up from the previous rendering

#### Scenario: Rotation and process restart
- **WHEN** the device rotates or the process is restarted while reading
- **THEN** the reader restores the same page and zoom state

#### Scenario: Large document
- **WHEN** a document has many pages
- **THEN** the reader renders only the pages needed for the current view and releases the rest, without loading the whole document into memory

### Requirement: Remember reading position
The application SHALL store the last reading position per paper locally, without an account, and SHALL restore it when that paper is reopened.

#### Scenario: Reopen a partly read paper
- **WHEN** a user reopens a paper they previously read part of
- **THEN** the reader resumes at the stored page and intra-page position

#### Scenario: Position survives process death
- **WHEN** the app process is killed while reading and the paper is reopened later
- **THEN** the stored position is restored, having been persisted during reading rather than only on exit

#### Scenario: First open
- **WHEN** a paper is opened for the first time
- **THEN** the reader starts at the first page

#### Scenario: Download is removed
- **WHEN** a completed download and its file are removed
- **THEN** the stored reading position for that paper is removed with them

#### Scenario: Continue reading
- **WHEN** papers have a stored reading position and their files are still present
- **THEN** the application offers a route back to them, ordered by most recently read

### Requirement: Refuse unreadable files
The reader MUST NOT present a file it cannot render as though it were the paper. It SHALL state what is wrong and offer the alternatives that remain.

#### Scenario: Placeholder demo file
- **WHEN** the file produced by the placeholder download implementation is opened
- **THEN** the reader refuses to present it as a paper and states that it is demo output, not the requested document

#### Scenario: Encrypted document
- **WHEN** a document requires a password
- **THEN** the reader reports that it cannot be opened and does not present blank or partial pages

#### Scenario: Malformed document
- **WHEN** a file is corrupt or is not a PDF
- **THEN** the reader reports the failure, leaves the download record intact, and offers the external-viewer handoff as an alternative

#### Scenario: A single page fails to render
- **WHEN** one page of an otherwise readable document fails to render
- **THEN** the reader marks that page as unavailable and continues to present the rest, rather than presenting a blank page as content
