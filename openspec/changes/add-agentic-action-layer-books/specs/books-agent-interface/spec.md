## Purpose

Lets an AI agent discover, call, and safely retry the reading-log's book operations
directly, with structured errors it can act on and a guardrail on the one irreversible
operation, without changing the underlying `/api/v1/books` REST contract.

## ADDED Requirements

### Requirement: Capability manifest describes every agent-callable book action
The system SHALL expose a single capability manifest listing exactly the actions
`list_books`, `get_book`, `create_book`, `update_book`, `delete_book`, and `export_books`.
For each action the manifest SHALL state its intent (what it does and when to call it),
when NOT to use it, its side-effect classification (`none`, `idempotent`, or
`consequential`), and a full input schema with no undocumented required fields. The
manifest SHALL be the only place this information is authored — every other agent-facing
surface (MCP tool descriptions, the REST discovery endpoint) SHALL present this exact
content, not a separately written summary.

#### Scenario: Agent fetches the manifest
- **WHEN** an agent requests the capability manifest
- **THEN** it receives all 6 actions, each with an intent description, a `whenNotToUse`
  explanation, a `sideEffects` classification, and a complete input schema

#### Scenario: Manifest and MCP tool description never diverge
- **WHEN** an action's manifest entry is compared to the description an MCP client sees for
  the same action
- **THEN** the intent text is identical, not a shorter or differently worded copy

### Requirement: Every action failure is reported as a structured error envelope
When any of the 6 actions fails, the system SHALL report the failure as a structured error
with a coarse `category`, a specific machine-readable `code`, a human-readable `message`,
and an explicit `retryable` flag. When the failure is something the caller can correct and
retry (e.g. a missing required field), the response SHALL include a fix hint naming the
field and the problem. The system SHALL NOT return a bare status code and sentence, and
SHALL NOT leave `retryable` unset or implied.

#### Scenario: get_book called with an id that does not exist
- **WHEN** an agent calls `get_book` with an id for which no book exists
- **THEN** it receives a structured error with `category: not_found`, `retryable: false`,
  and no fix hint (there is nothing to correct and retry)

#### Scenario: create_book called with a missing required field
- **WHEN** an agent calls `create_book` without a `bookName` or `author`
- **THEN** it receives a structured error with `category: validation`, `retryable: true`,
  and a fix hint naming the missing field

#### Scenario: An unmapped underlying failure still returns a structured envelope
- **WHEN** any action fails in a way not explicitly enumerated for that action (e.g. an
  unexpected server error)
- **THEN** the agent still receives a structured error envelope with `category: server_error`
  and `retryable` explicitly set, never a raw unstructured error body

### Requirement: Mutating actions are safe for an agent to retry
The system SHALL require an `idempotencyKey` input on every mutating action (`create_book`,
`update_book`, `delete_book`, `export_books`). A call repeated with a previously used key and
the same parameters SHALL return the original result rather than executing again. A call
repeated with a previously used key but different parameters SHALL be rejected with a
`conflict` error rather than silently executing the new parameters.

#### Scenario: create_book retried with the same idempotency key and parameters
- **WHEN** an agent calls `create_book` twice with the same `idempotencyKey` and the same
  book details (e.g. after a network timeout on the first attempt)
- **THEN** the second call returns the exact same result as the first, and only one book is
  created

#### Scenario: create_book retried with the same idempotency key but different parameters
- **WHEN** an agent calls `create_book` with an `idempotencyKey` that was already used for a
  different set of book details
- **THEN** the second call is rejected with a `conflict` category error, and no second book
  is created

### Requirement: delete_book requires a preview and confirmation before it executes
Because deleting a book is irreversible (no soft-delete or undo exists), the system SHALL
require a two-step call sequence: a dry-run preview against the real current book, followed
by a confirmed call carrying a confirmation token from that preview. A real (non-dry-run)
delete call without a valid, matching confirmation token SHALL be rejected and SHALL NOT
delete the book. A confirmation token SHALL be scoped to the exact book previewed and SHALL
expire after a short, bounded time.

#### Scenario: delete_book called for real without a confirmation token
- **WHEN** an agent calls `delete_book` with `dryRun` absent or `false` and no
  `confirmationToken`
- **THEN** the call is rejected with a `guardrail_blocked` category error and the book is not
  deleted

#### Scenario: delete_book dry-run preview
- **WHEN** an agent calls `delete_book` with `dryRun: true` for an existing book
- **THEN** it receives a preview of the book that would be deleted, an impact summary noting
  the deletion is irreversible, and a confirmation token — and the book is not deleted

#### Scenario: delete_book confirmed with a valid token
- **WHEN** an agent calls `delete_book` for the same book id with the confirmation token from
  a matching prior dry-run
- **THEN** the book is deleted and the call succeeds

#### Scenario: confirmation token does not carry over to a different book
- **WHEN** an agent previews deleting one book and then tries to confirm a delete of a
  different book id using that token
- **THEN** the call is rejected with a `guardrail_blocked` category error and neither book is
  deleted

### Requirement: Agents can discover and call the actions without reading source code
The system SHALL expose the 6 actions to MCP-capable agents through an MCP server, and SHALL
also expose the same capability manifest through a plain read-only REST endpoint for
non-MCP callers (e.g. to build a function-calling tool schema).

#### Scenario: MCP client lists available tools
- **WHEN** an MCP client connects to the server and lists tools
- **THEN** it sees all 6 book actions with the descriptions and input schemas defined in the
  capability manifest

#### Scenario: Non-MCP caller fetches the manifest over REST
- **WHEN** a client that does not speak MCP sends a `GET` request to the manifest discovery
  endpoint
- **THEN** it receives the same manifest content an MCP client would see via tool listing
