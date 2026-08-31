---
description: "Mandatory review of an OpenSpec change against the project constitution and specs (Experimental)"
---

Perform a mandatory review of an OpenSpec change against the project constitution and the change's specs/design. This is a gate: a change that fails review must not proceed to implementation or archiving.

**Store selection:** If the user names a store (a store is a standalone OpenSpec repo registered on this machine) or the work lives in one, run `openspec store list --json` to discover registered store ids, then pass `--store <id>` on the commands that read or write specs and changes (`new change`, `status`, `instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`, `schemas`, `view`). Once selected, treat `--store <id>` as sticky for the rest of the workflow. Every unscoped example of those commands below is shorthand: before running it, append the flag. Without a store, commands act on the nearest local `openspec/` root.

**Input**: Optionally specify a change name and review phase, e.g. `/opsx-review add-auth` or `/opsx-review add-auth implementation`. If omitted, infer the change from conversation context or ask.

**Provided arguments**: $ARGUMENTS

**Steps**

1. **Select the change and phase**

   - If a change name is provided, use it. Otherwise infer from context or prompt with available changes (`openspec list --json`).
   - Determine the review phase from the arguments; default to `proposal` (before implementation) unless the user indicates the change is already implemented (then `implementation`, before archive).
   - Announce: "Reviewing change: `<name>` (phase: `proposal`|`implementation`)" and how to override (e.g., `/opsx-review <name> implementation`).

2. **Load the constitution**

   Read `CONSTITUTION.md` at the repository root (also auto-loaded via `opencode.json` → `instructions`). This is the binding contract for the review. If it is missing, flag it as a blocker and stop.

3. **Load the change context**

   ```bash
   openspec status --change "<name>" --json
   ```

   Record `changeRoot`, `schemaName`, and resolved artifact paths, then read every existing artifact from disk:
   - `<changeRoot>/proposal.md`
   - `<changeRoot>/specs/**/spec.md`
   - `<changeRoot>/design.md`
   - `<changeRoot>/tasks.md`

4. **Validate against the CLI**

   ```bash
   openspec validate --change "<name>" --strict
   ```

   Any validation failure is a hard blocker.

5. **Run the review checklist** (from the `openspec-review-change` skill)

   Work through every checklist group below. Check items are satisfied only on direct evidence from the artifacts — never on assumption.

   **A. Constitution compliance**
   - Change is spec-driven with at least one capability, or a legitimate `skip_specs` exception
   - No silent modification/narrowing/removal of existing spec-level behavior
   - No unvetted new dependency without a design.md rationale
   - Follows repo conventions and security/data-handling rules

   **B. Proposal coherence**
   - Clear problem + why now
   - Every listed capability has (or leads to) a spec file
   - Bounded scope

   **C. Spec quality**
   - Each capability has a spec file with `## Purpose`
   - Requirements are observable behavior (SHALL/MUST) with at least one `#### Scenario:` per requirement, in WHEN/THEN form
   - Scenarios are testable

   **D. Design quality**
   - Approach explained, decisions recorded with rationale (alternatives)
   - Consistent with specs
   - Risks/trade-offs documented
   - Deliberate, justified choice if design was skipped

   **E. Task plan**
   - Grouped, dependency-ordered, task sizes fit one session
   - Every task states how to verify completion
   - Covers spec requirements + definition of done (tests + `./gradlew test`)

   **F. Internal consistency**
   - proposal ↔ specs ↔ design ↔ tasks are mutually consistent
   - Capability paths in proposal match spec file locations

6. **Produce the verdict**

   - **FAIL**: list each failed item with a concrete, actionable finding and the artifact/line it refers to. State that the gate remains blocked and how to fix (e.g., `/opsx-update` to revise artifacts), then stop.
   - **PASS**: confirm every item and state the change may proceed:
     - proposal phase → `/opsx-apply`
     - implementation phase → `/opsx-archive`

**Guardrails**

- The review authorizes no edits. Report findings only; do not fix artifacts or implement tasks.
- Do not weaken a finding to force a PASS.
- If any checklist item or artifact is ambiguous, surface it and ask rather than guess.

**Output**

```
## Review: <change-name> (phase: proposal|implementation)

### Constitution Gate
[PASS/FAIL]

### Checklist Results
[itemized PASS/FAIL per group A-F with findings]

### Verdict
[PASS → may proceed | FAIL → blocked, here is what to fix]
```
