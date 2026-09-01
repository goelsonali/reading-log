## 1. Dependencies

- [x] 1.1 Add `io.modelcontextprotocol.sdk:mcp-core:0.18.4`, `mcp-spring-webmvc:0.18.4`
      (`implementation`) and `mcp-json-jackson2:0.18.4` (`runtimeOnly`) to
      `build.gradle.kts`, per `design.md` Decision 1, and verify `./gradlew build`
      resolves dependencies successfully.

## 2. Capability manifest

- [x] 2.1 Write `src/main/resources/agent/books-manifest.json` with all 6 action entries
      (`list_books`, `get_book`, `create_book`, `update_book`, `delete_book`,
      `export_books`) matching the field table in `design.md` Decision 2, each entry valid
      against `.claude/skills/agentic-action-layer/contract/capability-manifest.schema.json`
      and with a `specRef` pointing at this change's spec file.
- [x] 2.2 Add a manifest-loading component that parses this file once at startup and
      verify a unit test asserts all 6 action names are present and every `sideEffects:
      consequential` entry has `requiresDryRun` and `costTier` set.

## 3. Error envelope, idempotency store

- [x] 3.1 Implement the error envelope type(s) matching
      `contract/error-envelope.schema.json` (`category`, `code`, `message`, `retryable`,
      optional `fixHint`/`retryAfterMs`/`originalStatus`) and verify a unit test builds one
      for each row of the `design.md` Decision 3 error-mapping table.
- [x] 3.2 Implement the in-memory idempotency dedup store (`design.md` Decision 4:
      `ConcurrentHashMap` keyed on `actionName`+`idempotencyKey`, fingerprint check, 48h
      TTL) and verify unit tests cover: first call executes and stores; replay with same
      fingerprint returns the stored result without re-executing; replay with a different
      fingerprint returns the `idempotency_key_reused` conflict error.

## 4. Guardrail token store (delete_book)

- [x] 4.1 Implement the in-memory guardrail token store (`design.md` Decision 5: token
      bound to `(action, bookId, state hash)`, 5-minute TTL, single-use) and verify unit
      tests cover: issuing a token on dry-run, consuming a valid token once, a second
      consumption attempt failing, an expired token failing, and a token issued for one
      book id failing to confirm a delete of a different book id.

## 5. BookAgentActions service

- [x] 5.1 Implement `BookAgentActions` wrapping `BookService` for `list_books` and
      `get_book` (read-only: no idempotency, no guardrail) and verify unit tests cover the
      success path and the `book_not_found` error mapping for `get_book`.
- [x] 5.2 Implement `create_book` and `update_book` on `BookAgentActions` (idempotency
      required; validation and not-found error mapping per `design.md` Decision 3) and
      verify unit tests cover: successful create/update, idempotent replay, the
      `book_name_required`/`author_required` validation errors, and `update_book`'s
      `book_not_found` error.
- [x] 5.3 Implement `delete_book` on `BookAgentActions` wiring the guardrail store from
      Task 4 in front of idempotency and the real delete, and verify unit tests cover the
      full sequence from spec scenarios "delete_book called for real without a
      confirmation token", "delete_book dry-run preview", "delete_book confirmed with a
      valid token", and "confirmation token does not carry over to a different book".
- [x] 5.4 Implement `export_books` on `BookAgentActions` (idempotency required; maps
      `BookExportException` to `export_write_failed`) and verify a unit test covers the
      success path and the export-failure mapping.

## 6. Agent-facing interfaces

- [x] 6.1 Wire the MCP server per `design.md` Decision 1: `WebMvcSseServerTransportProvider`
      bean, `McpSyncServer` bean registering all 6 actions as tools built from the manifest
      (Task 2) with `toolCall` handlers delegating to `BookAgentActions` (Task 5), and the
      transport's `RouterFunction` exposed as a Spring bean. Verify by starting the app and
      confirming an MCP client (or a raw JSON-RPC `tools/list` call against the mounted
      endpoint) lists all 6 tools with the manifest's descriptions and input schemas.
- [x] 6.2 Add `GET /api/v1/agent/books/manifest` returning the same parsed manifest from
      Task 2, and verify an integration test asserts its JSON body matches the manifest
      resource content (same source, per spec's "manifest and MCP tool description never
      diverge" scenario, applied here to the REST surface too).

## 7. Drift check and final verification

- [x] 7.1 Re-read `BookController`, `BookService`, and `GlobalExceptionHandler` and confirm
      the manifest's input schemas and the error-mapping table in `design.md` still match
      the actual code (per the agentic-action-layer skill's Article 0 drift check); fix any
      mismatch found before proceeding.
- [x] 7.2 Run `./gradlew test` and verify all tests (existing `BookControllerIntegrationTest`
      / `BookServiceTest` plus the new tests from Tasks 2-6) pass, confirming the existing
      `/api/v1/books` behavior is unchanged.
