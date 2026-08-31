## Purpose

Lets users save their entire reading log to a portable JSON file on demand, so the collection can be backed up, migrated, or inspected outside the running application.

## ADDED Requirements

### Requirement: Export all books to a JSON file
The system SHALL expose an endpoint that writes all current book entries to a JSON file on the server.

#### Scenario: Successful export of all books
- **WHEN** a user requests an export of all books
- **THEN** the system writes a JSON file containing the complete list of book entries

### Requirement: JSON file contains full book data
The exported JSON file SHALL be a JSON array containing every book entry, where each entry includes its id, book name, author, and review.

#### Scenario: Each exported entry has all fields
- **WHEN** a JSON file has been written
- **THEN** each entry in the file includes the book's id, book name, author, and review values

#### Scenario: Export reflects current entries
- **WHEN** a user has created book entries and then exports
- **THEN** the file contains exactly those entries, including any that were added after the application last exported

### Requirement: Export produces a server-side file
The system SHALL create the JSON file on the server filesystem when the export endpoint is called.

#### Scenario: File is created on demand
- **WHEN** the export endpoint is called
- **THEN** a JSON file is created on the server containing the current book data

### Requirement: Empty collection export
When there are no book entries, the export SHALL still produce a valid JSON file.

#### Scenario: Exporting with no books
- **WHEN** the user requests an export while there are no book entries
- **THEN** the system writes a valid JSON file representing an empty list
