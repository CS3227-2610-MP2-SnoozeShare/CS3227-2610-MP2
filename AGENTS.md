# AGENTS.md

Instructions for any AI agent working in this repository. Read this before touching anything.

## 1. `PROJECT_STATE.md` is the source of truth

The operator may switch between models, agents, and context windows. None of that history survives; `PROJECT_STATE.md` at the repo root does.

**Anything not written there did not happen.**

- **Before any work** — including answering questions or reading code — read
  `PROJECT_STATE.md` in full, then run `git log --oneline -20` and `git status` to confirm it still matches reality. Correct any drift you find before acting on the file.
- **Never reconstruct project state from source.** Code shows what is; it never shows what was decided, what was rejected, or what is half-built.
- **When the document and the repo disagree:** the repo wins on what the code does; the document wins on what was decided and why. Never silently edit a decision to match a stray commit — that erases the only record the decision existed.
- **If the file does not exist**, create it from
  `.claude/skills/maintaining-project-state/project-state-template.md`, populating it from `docs/superpowers/specs/`, `docs/superpowers/plans/`, git history, and the source tree. Then tell the operator what you inferred and what you guessed.
- **It is an index, not a library** — with one exception. Status, decisions and pointers live there; depth lives in the specs and plans it links to. But a fact that contradicts or post-dates every spec and plan — a deviation, a discovered trap, an accepted limitation — has nowhere else to live and belongs in the document in full.

## 2. Update it as you go, not at the end

Write to `PROJECT_STATE.md` immediately when any of these happen:

1. A workstream starts, blocks, passes review, finishes, or is abandoned — and whenever a task
   starts, finishes, or blocks, so the Progress cell stays current.
2. A session starts, changes what it's doing, spawns or retires an agent, or ends — update its
   row in the § How to Resume session table immediately; delete the row once its work is
   reflected in § Workstreams or the Done ledger. This is how multiple sessions or agents, on
   the same branch or different ones, hand off to each other without duplicating work.
3. The shape of the system changes — a new part, a moved responsibility, a changed boundary or
   data model — update that area in § Architecture in the same write.
4. The operator makes or changes a decision affecting design, scope, or priorities — record
   *who asked and why*, and what was rejected, not just the outcome.
5. A spec or plan is created or materially revised — link it from its workstream.
6. Work completes, or reality diverges from a plan — the Done ledger
   (`docs/project-state/done-ledger.md`), status, and Deviations.
7. The session is ending or context is running low — reconcile your session row (status, Doing,
   Last touched) so a different session can take over cold.
8. You merge a branch whose `PROJECT_STATE.md` diverged from yours — reconcile per
   `.claude/skills/maintaining-project-state/merging-across-branches.md` before trusting the
   file: append-only sections (Decisions, Deviations, Done ledger, sessions) keep both sides and
   renumber; prose describing what the system *is* needs real judgment, not both sides kept.

Batching these until the end of a session loses them, because sessions end unpredictably. **A
task is not complete until `PROJECT_STATE.md` reflects it.**

**The acid test, before you stop:** could a fresh agent, given only this file, take over any
session's row in § How to Resume correctly without asking a question? If not, fix the file.

Status vocabulary, used verbatim so it stays greppable:
`Not started` · `Spec'd` · `Planned` · `Building` · `Blocked — needs human` · `In review` ·
`Done` · `Abandoned`

**Two rules agents get wrong.** § Architecture is structured by the system, not by history: one
subsection per real part — data, a boundary such as auth or tenancy, a core engine, the
interface — each opening with what that part *is today*, followed by a table of the decisions
that shaped it. Decisions that changed how the work is done, not the system, stay in
§ Decisions; each decision appears once and keeps its ID. And the Workstream **Progress** cell
is a position, not a diary: at most ~15 words — where in the plan, what is in flight, and the
ID of whatever blocks or explains it (`Task 6/9 in flight: conflict engine`, `Task 3/8 stopped:
Q2`). What was built belongs in the plan and the Done ledger; why it changed belongs in
§ Decisions or § Deviations.

Cite sections by name — `§ Known Gaps` — not by number, so the file can be reordered.

## 3. Big changes need a spec and a plan. Every change gets recorded.

| Size | Examples | Before coding | After |
|---|---|---|---|
| **Big** — feature level and above | New user-facing capability; new module, service, or page; schema or data-model change; new dependency or architectural shift; cross-cutting refactor; anything spanning more than one session | A design spec at `docs/superpowers/specs/YYYY-MM-DD-<topic>-design.md`, an implementation plan at `docs/superpowers/plans/YYYY-MM-DD-<topic>.md`, and a Workstream row linking both | Workstream status + Done ledger entry |
| **Small** | Bugfix within an existing design; copy or style tweak; single-function refactor; added tests; config change | Nothing — just do it | Done ledger entry |

**Ceremony scales with size; the written record does not.** A one-line fix needs no spec and no
plan, but it still gets a Done line — and if it diverges from a plan, contradicts a spec, or
leaves a trap for the next agent, it also gets a Deviations entry. Only true no-ops
(formatting, a typo in a comment) pass unwritten.

Ambiguous size? Treat it as big.

If the operator asks to skip the spec and plan on a big change, say so once, then do as asked —
it is their call — and record the skip as a decision, so the missing document is visible to
whoever inherits the work.

## 4. Do not "fix" deliberate omissions

`PROJECT_STATE.md` § Known Gaps lists known gaps and accepted limitations. Those are decisions,
not a TODO list. If one looks wrong, raise it in § Needs a Human; do not quietly implement it.

**If an operator request collides with a recorded decision** — a § Known Gaps entry, the
out-of-scope list, or a decision in § Architecture or § Decisions — stop and confirm before
doing anything. Name the entry and ask which:
**keep the decision** (do not build it), or **reverse it** (build it, record a decision
reversing the entry, update or remove the entry, and open a new workstream if it falls outside
the current one). Act on neither until they answer, even if they said "just do it" — they may
not know the entry exists. This overrides the skip-the-spec rule in § 3 above. If the operator
is unavailable, stop and put the question in § Needs a Human.

Durable technical conventions live in § Conventions, and the current shape of each part of the
system in § Architecture. Read both before writing code, and add to them when you discover
something the next agent would otherwise learn the hard way.

## 5. Developer guide

`docs/DeveloperGuide.md` is the handover brief for developers taking over the project — scope,
goals, requirements, architecture, key decisions, diagrams, how to test. It holds only confirmed
work and changes only at checkpoints, so it may lag. **It never directs coding:** if it disagrees
with `PROJECT_STATE.md`, the state file wins. Do not edit the guide to fix it outside a checkpoint;
add `guide out of date: <what>` to the workstream's Progress instead.

- **Developer guide checkpoint:** `feature`
  (`feature` = each workstream once it is `Done` and the operator confirms it ·
  `phase` = when the operator declares a phase transition · `manual` = only on request)
- **Developer guide optional sections:** `none`
  (`none` · `user stories` · `use cases` · `user stories, use cases`)

**First write:** when the project's first spec is approved and no guide exists, propose seeding
it with only what the spec settles — overview, scope, requirements, glossary. No architecture,
no diagrams.

**When a workstream reaches `Done`:** set its **Guide** column to `Awaiting confirmation` (or `N/A`,
with the reason in Progress) and ask the operator whether it is confirmed; a yes makes it `Pending`.
At each checkpoint, **propose** the update — the sections and diagrams to add or change — and
**write nothing until the operator approves**; then set Guide to `Documented YYYY-MM-DD`. If the
operator holds it, it stays `Pending` with `guide held YYYY-MM-DD: <reason>` in Progress. Guide
values: `—` · `Awaiting confirmation` · `Pending` · `Documented YYYY-MM-DD` · `N/A`.

---

*Claude Code users: the full protocols are the skills in `.claude/skills/` —
`maintaining-project-state` (reconciliation, worked examples, failure modes) and
`update-documentation` (checkpoints, section rules, diagrams). This file is the portable subset.*
