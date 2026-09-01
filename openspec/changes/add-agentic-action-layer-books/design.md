## Context

`/api/v1/books` is a 6-endpoint Spring Boot REST API (`BookController` →
`BookService` → `BookRepository`/H2). See `proposal.md` for motivation. This design follows
`.claude/skills/agentic-action-layer` — its `contract/constitution.md` (5 non-negotiable
articles: traceability, discovery, recovery, safety, patience, consent) governs every
decision below, and each operation was classified against it before writing this design:

| Action | Endpoint | `sideEffects` | Why |
|---|---|---|---|
| `list_books` | `GET /api/v1/books` | `none` | read-only |
| `get_book` | `GET /api/v1/books/{id}` | `none` | read-only |
| `create_book` | `POST /api/v1/books` | `idempotent` | mutating, reversible via delete, low cost, no third party |
| `update_book` | `PUT /api/v1/books/{id}` | `idempotent` | mutating, full-replace, reversible, low cost |
| `delete_book` | `DELETE /api/v1/books/{id}` | `consequential` | **irreversible** — no soft-delete, no undo. Cost is zero and there's no third party, but Article 5 classifies on irreversibility alone; classify up, not down |
| `export_books` | `POST /api/v1/books/export` | `idempotent` | overwrites `books.json` from current DB state; reversible by re-exporting, no third party, no cost |

No OpenAPI file is committed to the repo (`springdoc-openapi-starter-webmvc-ui` only serves
a live spec at runtime). Per the skill's Article 0, the source of truth used for this design
is the actual controller/service/DTO/exception-handler code, read directly — not an
inference. This is recorded here as the documented exception, not silently treated as
equivalent to a real OpenAPI contract.

## Goals / Non-Goals

**Goals:**
- Satisfy all 5 constitution articles for all 6 actions, per the classification above.
- Keep the underlying `/api/v1/books` REST API completely untouched.
- Ground every new external dependency in a real, verified API (not documentation-summary
  guesses) before it's added to the build.

**Non-Goals:**
- No pagination or bulk operations for `list_books`/`export_books` — flagged as a risk below,
  not solved here.
- No async job wrapper for any action — every action's worst-case latency (single-row H2
  operation, or a JSON write of a small personal reading list) stays well under the default
  2000ms budget. Revisit if the dataset size assumption changes.
- No change to `GlobalExceptionHandler` or the plain REST error bodies it returns to
  non-agent callers of `/api/v1/books` directly.
- No authentication/authorization layer — out of scope; this repo has none today for the
  underlying API either.

## Decisions

### 1. New dependency: MCP Java SDK 0.18.4 (Constitution Article 4 — recorded here)

Adds `io.modelcontextprotocol.sdk:mcp-core:0.18.4`, `mcp-spring-webmvc:0.18.4`
(`implementation`), and `mcp-json-jackson2:0.18.4` (`runtimeOnly` — a ServiceLoader-discovered
Jackson 2.x `McpJsonMapper`; Spring Boot 3.2.5 ships Jackson 2.x, so the `2` variant is
required over `mcp-json-jackson3`).

**Why 0.18.4, not the newer `mcp`/`mcp-bom` 2.0.1 line**: `mcp-spring-webmvc` — the Spring
Web MVC transport, needed to mount the MCP server as ordinary Spring beans without an
embedded servlet of its own — tops out at `0.18.4` on Maven Central; it was not published
against the `1.x`/`2.x` restructuring (`maven-metadata.xml` for both artifacts checked
directly against `repo1.maven.org`, not a search index). Mixing `mcp-core:2.0.1` with
`mcp-spring-webmvc:0.18.4` risks a breaking API mismatch across that restructuring. Pinning
the whole stack to the last version where both are published together (`0.18.4`) avoids that.

**Why verified from source, not docs**: public docs/search summaries for this SDK gave
inconsistent version numbers and mixed two unrelated packages
(`org.springframework.ai.mcp.*` vs. `io.modelcontextprotocol.sdk.*`) in the same snippet.
Rather than build against that, the `0.18.4` sources jars for `mcp-core` and
`mcp-spring-webmvc` were downloaded from Maven Central and read directly. The real,
compilable shape confirmed this way:

```java
WebMvcSseServerTransportProvider transportProvider = WebMvcSseServerTransportProvider.builder()
    .messageEndpoint("/mcp/message")
    .build();
// transportProvider.getRouterFunction() -> RouterFunction<ServerResponse> Spring bean

McpSyncServer server = McpServer.sync(transportProvider)
    .serverInfo("reading-log-books", "0.0.1-SNAPSHOT")
    .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
    .toolCall(
        McpSchema.Tool.builder()
            .name("create_book")
            .description(manifestEntry.intent())          // verbatim from the manifest — Article 1
            .inputSchema(McpJsonDefaults.getMapper(), manifestEntry.inputSchemaJson())
            .build(),
        (exchange, request) -> {
            // request.arguments() : Map<String, Object>
            // delegate to BookAgentActions, map result/error to:
            return McpSchema.CallToolResult.builder()
                .structuredContent(resultOrErrorEnvelopeMap)
                .isError(failed)
                .build();
        })
    .build();
```

**Verified against a running server, not assumed**: the SDK exposes a configurable
`JsonSchemaValidator` hook for request-argument validation, but a live end-to-end test
(`initialize` → `tools/call create_book` with `bookName` omitted, over a real SSE session)
showed the call still reaching `BookAgentActions` rather than being rejected upstream — so
this design does not rely on the SDK to enforce "no undocumented required fields"; that
enforcement is `BookAgentActions`'s own explicit validation (Article 1's guarantee comes from
the manifest's declared `inputSchema` plus this application-level check, not from the
transport layer).

**Alternative considered**: hand-roll a minimal JSON-RPC endpoint that speaks just enough of
the MCP wire protocol (`tools/list`, `tools/call`) without the SDK. Rejected — it would drift
from the real MCP spec silently as the protocol evolves, which is exactly the kind of drift
this skill exists to prevent, just at the transport layer instead of the API layer.

### 2. Capability manifest as one JSON resource, loaded once

`src/main/resources/agent/books-manifest.json` holds all 6 manifest entries
(`capability-manifest.schema.json`-shaped). A single loader component parses it at startup.
Both the `GET /api/v1/agent/books/manifest` endpoint and the MCP tool registration read from
the same parsed manifest object — never two independently maintained descriptions (Article 1).

Manifest field values, derived from the classification table above and the actual
`BookRequest`/`BookResponse` shapes:

| Action | `sideEffects` | `idempotent` | `requiresDryRun` | `costTier` | Input (required) |
|---|---|---|---|---|---|
| `list_books` | `none` | — | — | — | *(none)* |
| `get_book` | `none` | — | — | — | `id` |
| `create_book` | `idempotent` | `true` | — | — | `bookName`, `author`, `idempotencyKey`; optional `review` |
| `update_book` | `idempotent` | `true` | — | — | `id`, `bookName`, `author`, `idempotencyKey`; optional `review` |
| `delete_book` | `consequential` | `true` | `true` | `free` | `id`, `idempotencyKey`; optional `dryRun`, `confirmationToken` |
| `export_books` | `idempotent` | `true` | — | — | `idempotencyKey` |

Each entry's `whenNotToUse` calls out the nearest confusable action (e.g. `update_book`'s
`whenNotToUse` tells an agent not to reach for it to delete a field — this API has no partial
patch, only full replace; `delete_book`'s tells an agent to prefer `export_books` first if it
wants a backup before removing an entry).

### 3. Error mapping (Article 2) — every distinct failure, not just the happy path split

Derived directly from `GlobalExceptionHandler` and each `BookService` method's real
exception paths:

| Action | Underlying failure | `category` | `code` | `retryable` | fix hint |
|---|---|---|---|---|---|
| `get_book`, `update_book`, `delete_book` | `BookNotFoundException` → 404 | `not_found` | `book_not_found` | `false` | — |
| `create_book`, `update_book` | blank `bookName` → 400 | `validation` | `book_name_required` | `true` | field `bookName` |
| `create_book`, `update_book` | blank `author` → 400 | `validation` | `author_required` | `true` | field `author` |
| `export_books` | `BookExportException` (disk I/O) → 500 | `server_error` | `export_write_failed` | `true` | — (not an input problem; retry after a delay) |
| any mutating action | idempotency key reused with different params | `conflict` | `idempotency_key_reused` | `false` | field `idempotencyKey` |
| `delete_book` (real call) | missing/expired/mismatched confirmation token | `guardrail_blocked` | `confirmation_required` | `true` | field `confirmationToken` |
| any action | anything else (Spring Boot's default error body today) | `server_error` | `unknown_error` | `false` | — |

### 4. Idempotency store (Article 3)

In-memory `ConcurrentHashMap<String, IdempotencyRecord>` keyed on `actionName + "\0" +
idempotencyKey`. `IdempotencyRecord` holds a fingerprint (hash of the caller's other
parameters) and the stored result (success payload or error envelope) with a 48-hour TTL,
checked lazily on access (no background sweep needed at this scale — matches the existing
app's in-memory-H2, single-instance footprint). A replay with a matching fingerprint returns
the stored result; a mismatched fingerprint returns the `idempotency_key_reused` conflict
above; nothing is executed twice.

**Alternative considered**: persist idempotency records in the H2 database alongside `Book`.
Rejected as unnecessary weight for a single-instance app whose own data already lives in an
in-memory H2 instance that resets on restart — the dedup store's lifetime guarantee (survive
a restart) doesn't need to exceed the data it's deduping against.

### 5. Guardrail token store (Article 5, `delete_book` only)

In-memory `ConcurrentHashMap<String, GuardrailToken>`. `dryRun: true` computes the preview
(the real current book, fetched fresh — "provably" side-effect-free because the dry-run code
path never calls `bookRepository.delete`), stores a token bound to `(action="delete_book",
bookId, a hash of the book's current state)`, and returns it with a 5-minute expiry. The real
call re-validates: token exists, not expired, single-use (deleted from the store on
successful consumption, valid or not), and its bound `bookId` matches the id being deleted —
satisfying "re-validate the bound parameters at confirmation time, not just the token's
validity" from `guardrail-contract.md`.

### 6. `BookAgentActions` service — the wrapping layer

One `@Service` with 6 methods, each: (a) resolve idempotency (short-circuit on replay/
conflict), (b) for `delete_book` only, resolve the guardrail (short-circuit on missing/
invalid token, or return the preview and stop on `dryRun: true`), (c) call the existing
`BookService` unchanged, (d) map any thrown exception through the table in Decision 3, (e)
record the idempotent result. `BookController`/`BookService` are not modified — this is a
layer in front, per the proposal's non-breaking claim.

## Risks / Trade-offs

- **[Risk]** `list_books`/`export_books` have no pagination; a very large reading log makes
  both slower, and `export_books`'s worst case could eventually exceed the 2000ms latency
  budget → **Mitigation**: none implemented now (Non-Goal); if the dataset size assumption
  changes, revisit as an async job per Article 4 rather than silently raising the budget.
- **[Risk]** In-memory idempotency/guardrail stores are lost on restart → **Mitigation**:
  acceptable — matches the existing app's own in-memory H2 data lifetime; a retry racing a
  restart re-executes exactly once, which is the same safety bar the underlying API had
  before this change (none), not a regression.
- **[Risk]** `export_books`'s idempotency is approximate — replaying with the same key
  returns the original file-write result, but if the DB changed between the original call and
  a much later "replay" with a stale mental model of a retry, an agent could be surprised the
  file wasn't refreshed → **Mitigation**: the 48-hour TTL keeps this window short; documented
  in the manifest's `export_books` intent text so an agent knows a replay returns the
  originally-exported snapshot, not a fresh one.
- **[Trade-off]** Pinning to MCP SDK `0.18.4` instead of the `2.x` line means picking up
  `2.x`'s Spring WebMVC transport later requires a follow-up change once (if) that artifact
  is published — acceptable given the alternative is an unverified cross-major-version mix.

## Migration Plan

Purely additive — no existing endpoint, table, or response shape changes. Deploy as a normal
release; no data migration, no rollback complexity beyond a normal revert (removing the new
package, resource file, and dependencies).

## Open Questions

None — the two decisions that would otherwise be open (exact MCP SDK version, and the
manifest/error-mapping content) were resolved above by reading the real source and the real
controller/service code rather than deferred.
