## Why

The reading log currently keeps all book entries only in the in-memory H2 database, so there is no way to persist or share the data outside the running application. Readers want an easy way to save their entire collection to a JSON file so it can be backed up, migrated, or inspected offline.

## What Changes

- Add a new REST endpoint that writes all current book entries to a JSON file.
- The generated JSON file contains the full list of book entries (id, book name, author, review).
- The file is created on the server (e.g., in the application's working directory) and is produced on demand when the endpoint is called.

No existing endpoints, request/response contracts, or the book CRUD behavior are removed or changed, so this is non-breaking.

## Capabilities

### New Capabilities
- `book-export`: Ability to export all book entries to a JSON file on demand via a new REST endpoint.

### Modified Capabilities
<!-- None. This introduces a new capability only; no existing spec-level behavior changes. -->

## Impact

- **Code**: New endpoint in `BookController` and a corresponding method in `BookService`; a small file-writing helper may be introduced. No changes to `Book`, `BookRequest`, `BookResponse`, or `BookRepository`.
- **API**: New endpoint under `/api/v1/books` (e.g., `POST /api/v1/books/export`).
- **Dependencies**: None new; Java standard library file I/O suffices.
- **System**: Produces a JSON file on the server filesystem; no external systems involved.
