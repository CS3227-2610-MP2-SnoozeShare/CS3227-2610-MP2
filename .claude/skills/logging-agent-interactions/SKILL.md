---
name: logging-agent-interactions
description: Use when explicitly invoked at the end of an agent session to record that session's material project interactions.
---

# Logging Agent Interactions

## Purpose

Maintain a concise, append-only record of material user/agent interactions for this repository.
`PROJECT_STATE.md` remains the source of truth for current decisions, workstream status, and
handoff state; this log preserves interaction evidence and context.

## When to run

Invoke this skill manually once at the end of an agent session (for example, with
`$logging-agent-interactions` or the host's equivalent explicit skill invocation). Do not invoke it
automatically on every turn. At invocation time, review the session transcript and create an entry
only when a user input materially affects the project, including when it:

- requests or changes work, requirements, scope, priorities, or constraints;
- makes, approves, rejects, or reverses a design or process decision;
- approves a specification, plan, implementation step, branch strategy, or handoff;
- reports a bug, failure, blocker, or unexpected behavior;
- requests status, review, verification, or handoff information; or
- resolves a question recorded in `PROJECT_STATE.md`.

Do not log greetings, thanks, repeated non-material acknowledgements, casual discussion with no
project consequence, or routine tool output. A short response such as “yes” is material when it
approves a plan or decision. If the session contains no material interactions, do not create an
empty log file.

## Session file

Use one file for the whole agent session. Create it directly under `logs/` using the established
timestamped filename convention:

```text
logs/YYYY-MM-DD_HH-mm-ss_<branch>.md
```

The filename timestamp format is exactly `YYYY-MM-DD_HH-mm-ss`. Replace `/`, spaces, and other unsafe
branch characters with `-`; preserve the branch name in a recognizable slug. The filename stem is
the canonical session key, so do not use a copied numeric `S2`-style label as the log identity.
Numeric `S` labels in `PROJECT_STATE.md` are human-readable handoff labels only. If two sessions
must run on the same branch at the same second, append a short unique nonce while retaining the
required timestamp and branch portions. If session metadata is uncertain, use the safest unique
filename and state the uncertainty in the entry instead of blocking project work.

Do not use or recreate `logs/LLM_interactions.md`. There is intentionally no consolidated log yet.
Parallel sessions get separate files because their session-start timestamps and/or branch slugs
must make the targets distinct.

## Session-file and entry format

Start the file with this session-level header, then append exactly one entry for each material user
input. Entries must be kept in chronological order and must not contain timestamp metadata. Never
overwrite existing entries.

```markdown
# Agent Session Interaction Log

- Session key: `2026-09-23_14-44-51_w1`
- Branch: `w1`
- Workstream: W1

## 2026-09-23_14-44-51_w1__01 — request

### User input

Complete user input, verbatim except redactions.

### Agent response summary

- Factual action taken.
- Files changed and tests run.
- Decisions, blockers, or follow-up, when relevant.

### Outcomes

- Files changed: `path/to/file`
- Tests: `not run` or exact command/result
- Blockers: `none` or the blocking question
- Follow-up: next concrete action
```

The interaction ID must be unique within the repository and include the canonical session key plus
an entry sequence, for example `2026-09-23_14-44-51_w1__01`. Use a concise type such as `request`,
`decision`, `approval`, `blocker`, `failure`, `verification`, `status`, or `handoff`.

## Redaction

Preserve the user's wording except for high-confidence secrets and clearly private personal
information. Redact passwords, API keys, access tokens, private keys, and similar credentials with
explicit markers such as `[REDACTED: API key]`. Do not repeat a redacted value in the agent
summary. Do not guess that ordinary project names, requirements, or source code are confidential.

## Manual end-of-session procedure

When explicitly invoked at session end:

1. Identify the branch, session key, and workstream.
2. Review the complete session transcript and select material user inputs.
3. Redact only high-confidence confidential values.
4. Create the single session file, if at least one material input exists.
5. Append exactly one entry for each selected material input in chronological order, without
   timestamp metadata.
6. Summarize each response factually; do not claim work that did not happen.
7. Verify that every entry exists in the session file.

If writing fails, report the logging failure to the user, continue the requested project work, and
do not claim successful recording.

## Examples

Material:

```text
User: Store this as part of W1 and use per-session SGT files.
Result: Append one entry describing the approved scope and resulting files.
```

Not material:

```text
User: Thanks.
Result: Do not create an entry.
```

Redaction:

```text
User: Test with API key sk-example-secret.
Recorded: Test with API key [REDACTED: API key].
```

Session target:

```text
Session on branch w1, with entries ordered chronologically
→ logs/2026-09-23_14-44-51_w1.md
```
