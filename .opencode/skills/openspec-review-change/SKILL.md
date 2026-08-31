---
name: openspec-review-change
description: Mandatory review of an OpenSpec change against the project constitution and the change's specs/design. Use when the user wants to review or gate a change (before implementation or before archive) to ensure it complies with CONSTITUTION.md, has valid specs, and is internally coherent. Use for running `/opsx-review`.
allowed-tools: Bash(openspec:*)
license: MIT
compatibility: Requires openspec CLI.
---

# Mandatory Change Review

Perform a strict, constitution-enforced review of an OpenSpec change. This is a
**gate**: a change that fails review must NOT proceed to implementation or
archiving until the findings are fixed.

**Store selection:** If the user names a store (a store is a standalone OpenSpec
repo registered on this machine) or the work lives in one, run `openspec store
list --json` to discover registered store ids, then pass `--store <id>` on the
commands that read or write specs and changes. Once selected, treat `--store
<id>` as sticky. Without a store, commands act on the nearest local `openspec/`
root.

**Input**: Change name (kebab-case) and the review phase. If omitted, infer the
change from context or ask. Phase is `proposal` (before implementation) or
`implementation` (before archive).

---

## Steps

1. **Load the constitution.**

   Read the repository's `CONSTITUTION.md` (also auto-loaded into the session via
   `opencode.json` → `instructions`). This is the binding contract the review
   enforces. If the file is missing, flag it as a blocker — no change can be
   reviewed without the guardrails in place.

2. **Locate the change and its context files.**

   ```bash
   openspec status --change "<name>" --json
   ```

   Record `changeRoot`, `schemaName`, and the resolved artifact paths
   (`proposal`, `specs/**/*.md`, `design`, `tasks`). Read every existing artifact
   from disk (proposal.md, specs/<capability>/spec.md, design.md, tasks.md).

3. **Validate against the CLI.**

   ```bash
   openspec validate --change "<name>" --strict
   ```

   Any validation failure is a hard blocker. Do not proceed on a failing change.

4. **Review checklist.** Work through every item. A block fails the review.

### A. Constitution compliance
- [ ] The change is expressed as a spec-driven change with at least one
      capability, or a legitimate `skip_specs` exception (refactor/tooling/docs).
- [ ] The change does not silently modify, narrow, or remove any existing
      spec-level behavior.
- [ ] No unvetted new dependency is introduced without a design.md rationale.
- [ ] The change follows the repository's code/architecture conventions and its
      security/data-handling rules (see CONSTITUTION.md).

### B. Proposal coherence ("what" and "why")
- [ ] Proposal clearly states the problem and why it is needed now.
- [ ] Every capability listed in the proposal has (or leads to) a corresponding
      spec file.
- [ ] Scope is bounded; the proposal does not creep beyond what is described.

### C. Spec quality ("what the system must do")
- [ ] Each capability has a spec file with a `## Purpose`.
- [ ] Requirements are expressed as observable behavior (SHALL/MUST) with at
      least one `#### Scenario:` per requirement, in WHEN/THEN form.
- [ ] Scenarios are testable. Every scenario maps to a potential test case.

### D. Design quality ("how")
- [ ] Design explains the approach and records key decisions with rationale
      (alternatives considered).
- [ ] Design is consistent with the specs; no design decision contradicts a spec
      requirement.
- [ ] Risks/trade-offs are documented with mitigations where applicable.
- [ ] Skipping design must be a deliberate, justified choice when the change is
      simple.

### E. Task plan
- [ ] Tasks are grouped, ordered by dependency, and each is small enough for one
      session.
- [ ] Every task states how to verify completion (test, command, observable
      behavior).
- [ ] Tasks cover the spec's requirements and the definition of done (tests +
      `./gradlew test`).

### F. Internal consistency
- [ ] proposal ↔ specs ↔ design ↔ tasks are mutually consistent. No requirement
      described in one artifact is missing or contradicted in another.
- [ ] Capability paths in the proposal match the spec file locations.

5. **Output the verdict.**

   - **FAIL**: list each failed checklist item with a concrete, actionable
     finding and the artifact/line it refers to. Tell the user what to fix and how
     (e.g., `/opsx-update` to revise artifacts) and that the gate remains blocked.
   - **PASS**: confirm every item, then state that the change may proceed:
     - proposal phase → implementation via `/opsx-apply`
     - implementation phase → archiving via `/opsx-archive`

**Guardrails**

- The review authorizes no edits. Report findings only; let the user revise.
  Do not fix the change, edit artifacts, implement tasks, or archive.
- Do not weaken a finding to force a PASS — the constitution is non-negotiable.
- If a review item is ambiguous, surface the ambiguity and ask rather than guess.
