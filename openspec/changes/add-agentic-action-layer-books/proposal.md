## Why

The `/api/v1/books` REST API was built for a human-operated client (a browser, a developer
calling it directly): it returns bare 4xx/5xx bodies with a sentence or a field-map, has no
way to retry a mutation safely, and lets an agent permanently delete a book with a single
call and no preview. An AI agent calling this API today can't discover what it does beyond
a Swagger summary, can't recover from a failure without a human reading the error, can't
retry safely, and can irreversibly delete a reading-log entry with no warning. This change
wraps the existing 6 endpoints with an Agentic Action Layer (per
`.claude/skills/agentic-action-layer`) so an agent can call this API directly and safely,
without changing the underlying REST API itself.

## What Changes

- Add a capability manifest (one JSON resource, the single discovery surface) describing all
  6 book operations as agent-callable actions: `list_books`, `get_book`, `create_book`,
  `update_book`, `delete_book`, `export_books`.
- Add a structured error envelope for every distinct failure mode each operation can produce
  (not just a generic 4xx/5xx split), so an agent can branch on `category`/`code` and act on
  `retryable`/`fixHint` without a human interpreting a status code.
- Add an idempotency dedup store and require an `idempotencyKey` on every mutating action
  (`create_book`, `update_book`, `delete_book`, `export_books`), so an agent's retry replays
  the original result instead of double-executing.
- Add a dry-run + confirmation-token guardrail to `delete_book` — the only operation
  classified `consequential` (it's irreversible: no soft-delete, no undo) — so an agent must
  preview the delete and hold a short-lived, parameter-scoped confirmation token before the
  real delete executes.
- Expose the 6 actions to agents via an MCP server (new endpoint under `/mcp`), plus a plain
  `GET /api/v1/agent/books/manifest` endpoint that serves the same manifest for non-MCP
  discovery (e.g. building a function-calling schema).
- **BREAKING**: none. The existing `/api/v1/books` REST endpoints, request/response shapes,
  and status codes are untouched — this change adds a new layer in front of them, it does not
  modify `BookController`, `BookService`, or their contracts.

## Capabilities

### New Capabilities
- `books-agent-interface`: an Agentic Action Layer over the 6 existing `/api/v1/books`
  operations — capability manifest, structured error envelope, idempotency, the delete-book
  guardrail, and MCP + REST discovery surfaces for agents calling this API.

### Modified Capabilities
<!-- None. The underlying book-management behavior (create/read/update/delete/export) is
     unchanged; this change adds a new agent-facing interface in front of it, it does not
     alter any existing capability's requirements. -->

## Impact

- **Code**: new `com.readinglog.app.agent` package (error envelope, idempotency store,
  guardrail token store, action-wrapping service, MCP server wiring); new
  `src/main/resources/agent/books-manifest.json`; new `GET /api/v1/agent/books/manifest`
  controller endpoint. No changes to `BookController`, `BookService`, `Book`, `BookRequest`,
  `BookResponse`, `BookRepository`, or `GlobalExceptionHandler`.
- **API**: new endpoints only — `GET /api/v1/agent/books/manifest` and an MCP server mounted
  under `/mcp` (exact path decided in design.md). No existing endpoint's contract changes.
- **Dependencies**: new — `io.modelcontextprotocol.sdk:mcp-core`,
  `io.modelcontextprotocol.sdk:mcp-spring-webmvc`, `io.modelcontextprotocol.sdk:mcp-json-jackson2`
  (all `0.18.4`, verified against Maven Central and the real source jars). Rationale and
  version verification recorded in `design.md` per Constitution Article 4.
- **System**: adds two new in-memory stores (idempotency dedup, guardrail confirmation
  tokens) with TTL-based expiry; no new external systems, no persistence changes to the H2
  database.
