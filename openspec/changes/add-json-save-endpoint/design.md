## Context

The reading log is a Spring Boot 3.2.5 (Java 21) REST API exposing CRUD over `/api/v1/books`. Book entries are persisted to an in-memory H2 database via `BookRepository` (JPA). The current `BookController`/`BookService` pattern is simple: controller delegates to service, which maps domain entities to `BookResponse` records. No file I/O exists today. This change adds a new endpoint that writes the current book collection to a JSON file on the server. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- Add an endpoint that serializes the full book collection to a JSON file on the server.
- Reuse the existing domain, repository, and response-mapping patterns.
- Keep the change small and self-contained with no new third-party dependencies.

**Non-Goals:**
- No changes to existing CRUD endpoints, request/response DTOs, or the entity model.
- No database persistence changes (JSON file is an export artifact, not a secondary source of truth).
- No UI to download the file; the export is written server-side.

## Decisions

- **Endpoint shape:** `POST /api/v1/books/export`.
  Rationale: an export is an action that produces a resource/side effect; `POST` avoids the caching and idempotency semantics of `GET` and needs no request body. Alternative considered: `GET /api/v1/books/export` (rejected: implies safe/read-only semantics and is cacheable).
- **Serialization:** Use Jackson's `ObjectMapper` (already provided by `spring-boot-starter-web`) to serialize the list of `Book` entities into a JSON array.
  Rationale: zero new dependencies and reflects the DTO field order. Alternative considered: adding a JSON library (rejected — unnecessary).
- **Data written:** Serialize the `Book` entities directly, which yields the required id, bookName, author, and review fields.
  Rationale: matches the export requirements with minimal mapping. Alternative considered: reusing `BookResponse` (rejected — id and field names already align, mapping through records adds no value).
- **File location:** A `books.json` file written to the application working directory, derived deterministically (e.g., `./books.json`).
  Rationale: simple, predictable, and matches "save to a json file". Alternative considered: configurable path via `application.yml` (can be added later; kept out of scope to stay minimal).
- **Component split:** A service method in `BookService` (e.g., `exportToJson`) that fetches all books, serializes them, and writes the file; the controller adds a thin `@PostMapping` handler.
  Rationale: follows the existing service-layer pattern and keeps the controller thin.
- **Error handling:** On write failure, throw a runtime exception that falls through to the existing `GlobalExceptionHandler` so the client gets a server error rather than a silent failure.
  Rationale: reuses existing error plumbing.

## Risks / Trade-offs

- [Filesystem write can fail (permission, disk full, missing directory)] → Catch I/O exceptions and surface as an HTTP 500 via `GlobalExceptionHandler`; keep failure explicit rather than swallowing it.
- [Export file can grow unbounded for large collections] → Acceptable for a personal reading log; out of scope to chunk/stream since H2 is in-memory and data volumes are small.
- [Concurrent export writes to the same file could interleave] → Use a timestamped or fixed-but-atomically-replaced write; for a single-user local app this is low risk and not addressed in this change.
