# Agent Interaction Logging Skill — Implementation Plan

Workstream: W1 support tooling

1. Create `.claude/skills/logging-agent-interactions/SKILL.md` with explicit end-of-session
   invocation guidance, the materiality gate, collision-safe SGT/session filename convention,
   redaction rules, entry schema, append-only procedure, and failure behavior.
2. Remove the obsolete `logs/LLM_interactions.md` placeholder and update repository references to
   describe per-session files under `logs/`.
3. Update `PROJECT_STATE.md` immediately with the W1 support-tooling spec/plan, active session, and
   current logging architecture; add the completed work to the Done ledger when verified.
4. Create the current session log using the SGT session-start timestamp and `w1` branch slug,
   then append this material interaction after the skill is available.
5. Validate frontmatter and skill structure with the bundled quick validator, run `git diff --check`,
   and run the repository test command.
