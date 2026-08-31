# Personal Reading Log

A simple Spring Boot REST API to track books you've read: book name, author, and your review.

## Tech Stack

- Java 21
- Spring Boot 3.2.5 (Web, Data JPA, Validation)
- H2 in-memory database
- Gradle

## Run

```bash
./gradlew bootRun
```

The app starts on `http://localhost:8081`.

- Swagger UI: http://localhost:8081/swagger-ui/index.html
- H2 console: http://localhost:8081/h2-console (JDBC URL: `jdbc:h2:mem:readinglog`, user: `sa`, no password)

## API

| Method | Path              | Description          |
|--------|-------------------|-----------------------|
| POST   | `/api/v1/books`      | Create a book entry  |
| GET    | `/api/v1/books`      | List all book entries|
| GET    | `/api/v1/books/{id}` | Get a book entry     |
| PUT    | `/api/v1/books/{id}` | Update a book entry  |
| DELETE | `/api/v1/books/{id}` | Delete a book entry  |
| POST   | `/api/v1/books/export` | Save all book entries to a JSON file |

### Example

```bash
curl -X POST http://localhost:8081/api/v1/books \
  -H "Content-Type: application/json" \
  -d '{"bookName": "Clean Code", "author": "Robert C. Martin", "review": "Great read"}'
```

## Test

```bash
./gradlew test
```

## Spec-Driven Development

This repository uses **spec-driven development** via [OpenSpec](https://openspec.dev). Requirements are captured as behavior specs, planned as changes, and implemented step by step.

### How it works

- **Specs** (`openspec/specs/`) define what the system must do — observable behavior, scenarios, and contracts.
- **Changes** (`openspec/changes/<change-name>/`) contain the planning artifacts for each piece of work, in this order:
  - `proposal.md` — what & why
  - `specs/<capability>/spec.md` — the behavior contract (delta)
  - `design.md` — how to implement it
  - `tasks.md` — the implementation checklist
- Completed changes are archived under `openspec/changes/archive/`.

### Getting started

You'll need the `openspec` CLI (see the [OpenSpec docs](https://openspec.dev) to install it).

```bash
# Confirm the CLI works and see current state
openspec --version
openspec list            # list active changes

# Start a new change (proposal, specs, design, tasks)
openspec new change "<change-name>"

# Incrementally create each artifact
openspec instructions proposal --change "<change-name>"
openspec instructions specs --change "<change-name>"
openspec instructions design --change "<change-name>"
openspec instructions tasks --change "<change-name>"

# Validate your change and its specs
openspec validate --change "<change-name>" --strict

# Check status as you go
openspec status --change "<change-name>"
```

Once the planning artifacts are ready, implement the tasks, then archive the completed change. Interactive helpers that walk through this flow (propose, apply, verify, archive, review) are available in this repo under `.opencode/commands/` (e.g. the `opsx-*` commands).

## Project Guardrails (CONSTITUTION.md)

The repository's non-negotiable rules are defined in **[`CONSTITUTION.md`](./CONSTITUTION.md)**. This file is the binding contract for every change, proposal, review, and line of code

The constitution is auto-loaded into every session (via `opencode.json` → `instructions`) and is the first checkpoint of the proposal workflow. It is enforced through the mandatory review workflow (`/opsx-review`).
