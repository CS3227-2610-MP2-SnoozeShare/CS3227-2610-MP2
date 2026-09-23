# Agent Interaction Logging Skill — Design

## Status

Approved by the operator on 2026-09-23 as a W1 support-tooling task.

## Goal

Provide a repository-local skill that records material agent/user interactions by agent session.
The record should preserve enough context for course review and future handoff without becoming a
second project-state source of truth.

## Decisions

- Store one append-only Markdown file per agent session directly in `logs/`.
- Name each session file `YYYY-MM-DD_HH-mm-ss_<branch>.md`, using the session start time in SGT
  (`Asia/Singapore`, UTC+08:00) and a filesystem-safe branch slug.
- Append every material interaction from that session to its session file; do not create a
  consolidated log yet.
- Use the filename stem (`YYYY-MM-DD_HH-mm-ss_<branch>`) as the canonical session key. Numeric
  `S` labels in `PROJECT_STATE.md` are handoff labels, not globally unique log identifiers.
- Remove the placeholder `logs/LLM_interactions.md`; it is not a canonical target under this
  design.
- Invoke the skill manually once at the end of an agent session. At that point, review the full
  transcript and write entries only for user inputs that change project direction, requirements,
  scope, design, implementation, verification, or handoff.
- Preserve the complete user input except for high-confidence confidential values. Redact secrets
  and clearly private personal information with explicit markers.
- Keep `PROJECT_STATE.md` authoritative for decisions, workstream status, and resumption state;
  the interaction log is an evidence/context record.

## Entry shape

Each session file starts with the session key, SGT start time, branch, and workstream header. Each
entry then contains a unique interaction ID derived from the canonical session key and entry
sequence, an SGT timestamp, interaction type, complete redacted user input, concise factual
agent-response summary, files changed, tests, blockers, and follow-up.

## Trigger policy

The skill is manually invoked at session end. It reviews the complete transcript and writes for
requests, decisions, requirement/scope changes, approvals, blockers, failures, verification
results, status requests, and handoffs. It does not write for greetings, thanks, repeated
non-material acknowledgements, or routine tool output with no project consequence. This reduces
per-turn overhead but cannot recover a session that ends before the manual invocation.

## Failure behavior

If the log cannot be written, the agent reports that failure but continues the requested project
work. The agent must not claim that an entry was recorded when it was not.
