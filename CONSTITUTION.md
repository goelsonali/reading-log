# Project Constitution

This file is the **binding contract** for every change, proposal, review, and line
of code in this repository. It is loaded automatically into every opencode
session (via `opencode.json` → `instructions`) and is the *first* checkpoint of
the proposal workflow and every review.

These are non-negotiable guardrails. A change, proposal, design, or implementation
that violates the constitution MUST NOT be accepted, planned, or archived. If you
believe a rule is wrong, propose changing the constitution itself — do not silently
bypass it.

---

## 1. Spec-Driven Development is Mandatory

- Every behavior change vehicle MUST go through an OpenSpec change with the full
  artifact chain: `proposal.md` → `specs/<capability>/spec.md` → `design.md` → `tasks.md`.
- Requirements MUST be expressed as **observable behavior** (SHALL/MUST with
  testable WHEN/THEN scenarios), not as implementation steps.
- Every change MUST declare at least one capability or explicitly set
  `skip_specs: true` (refactor/tooling/docs only). Never invent a requirement just
  to satisfy validation.
- A change MUST be `openspec validate --strict` clean, and MUST pass its own
  review (see the review workflow) before it is archived.

## 2. No Spec-Level Behavior is Silently Changed

- Do not drop, narrow, defer, or add exceptions to behavior that a spec
  guarantees. If a spec must change, that is itself a change (`openspec update`) —
  never a silent implementation shortcut.
- Implementation must satisfy the delta specs exactly as written; anything beyond
  the spec is out-of-scope scope creep and must be surfaced, not absorbed.

## 3. Review Gate is Non-Negotiable

- Proposal review MUST run before a change is ready to implement.
- Implementation review MUST run before a change is archived.
- Review is complete only when the reviewer confirms every checklist item AND the
  change is consistent with the specs, design, and this constitution.

## 4. Code and Architecture Conventions

- Java 21, Spring Boot 3.2.5. Controllers are thin; business logic lives in
  `@Service` classes; data access is behind `BookRepository` (JPA).
- API roots are versioned (`/api/v1/...`). Keep request/response as records/DTOs;
  never leak JPA entities in REST responses unless the spec requires it.
- No new third-party dependencies without recording the decision and rationale in
  the change's `design.md`.
- Keep changes minimal and focused: one change, one coherent capability.

## 5. Tests Are Part of the Definition of Done

- Every spec scenario is a potential test. Add unit and/or integration tests that
  exercise each externally observable behavior the change introduces.
- `./gradlew test` MUST pass before a change is marked complete.
- A change is not "done" until its tests exist, pass, and its risk/error paths are
  covered (e.g., failure handling mapped to a defined HTTP status).

## 6. Security and Data Handling

- Never commit secrets, keys, or credentials. Env-driven configuration only.
- Follow the existing `GlobalExceptionHandler` pattern: map expected failures to
  explicit HTTP statuses; do not leak stack traces to clients.
- Validate inputs with Bean Validation (`@Valid`), matching the existing DTO style.

## 7. Exceptions and Amending the Constitution

- If a task cannot satisfy a constitution rule, STOP and surface it to the user.
  Do not proceed, defer, or weaken the rule silently.
- Changes to this constitution are themselves a change and require the same
  review rigor.
