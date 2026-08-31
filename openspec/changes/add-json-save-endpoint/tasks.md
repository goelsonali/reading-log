## 1. Service Layer

- [x] 1.1 Add an `exportToJson()` method to `BookService` that fetches all books, serializes them to a JSON array with Jackson's `ObjectMapper`, and writes them to `./books.json`; verify it compiles (`./gradlew compileJava`) and a file is produced when called.
- [x] 1.2 Handle I/O failures (permission, disk full) by throwing a runtime exception so `GlobalExceptionHandler` returns an HTTP 500; verify via a unit test that a write failure propagates an exception.

## 2. Controller Layer

- [x] 2.1 Add a `POST /api/v1/books/export` handler in `BookController` that delegates to `BookService.exportToJson()`; verify the endpoint is registered and compiles (`./gradlew compileJava`).

## 3. Tests

- [x] 3.1 Add service unit tests covering a successful export (file written with all book fields) and an empty-collection export producing a valid empty JSON file; verify `./gradlew test` passes.
- [x] 3.2 Add an integration test that calls `POST /api/v1/books/export` and asserts an HTTP 200/2xx with a `books.json` file created containing the seeded entries; verify `./gradlew test` passes.
- [x] 3.3 Update the README API table to document the new export endpoint; verify the table lists `POST /api/v1/books/export`.
