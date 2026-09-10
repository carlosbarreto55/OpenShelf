## ADDED Requirements

### Requirement: Demo-mode disclosure
When the configured remote mode is fake, the application SHALL disclose that placeholder data is in use, SHALL allow the user to dismiss the full explanation, and SHALL retain a persistent compact indicator that reopens it. The disclosure MUST NOT occupy layout on every destination for the life of the installation.

#### Scenario: First launch in demo mode
- **WHEN** a user launches a demo-mode build for the first time
- **THEN** the system presents a dismissible explanation stating that the data is placeholder data and that no live backend is connected

#### Scenario: Explanation is dismissed
- **WHEN** the user dismisses the explanation
- **THEN** the system does not present it again on subsequent launches, and no destination reserves layout for it

#### Scenario: Demo state remains discoverable
- **WHEN** the explanation has been dismissed and the build is still in demo mode
- **THEN** a compact indicator remains available from the search screen, and activating it reopens the full explanation

#### Scenario: Real backend is selected
- **WHEN** the configured remote mode is not fake
- **THEN** no demo explanation or demo indicator is presented anywhere in the application

#### Scenario: Disclosure at the point of consequence
- **WHEN** a placeholder action would produce a result that could be mistaken for real content
- **THEN** the system discloses the demo behaviour at that action, independently of whether the first-run explanation was dismissed
