# Section Rules

What each section of `docs/DeveloperGuide.md` holds, where it comes from, and what stays out.
Skeleton: [guide-template.md](guide-template.md). Diagrams: [diagrams.md](diagrams.md).

## Never in the Guide

- **Status, progress, TODOs, in-flight or unconfirmed work.** That is `PROJECT_STATE.md`.
- **Secret values.** Environment variable *names* only.
- **Process decisions** — how agents work, how documents are kept. Only decisions that shape the
  product or its architecture.
- **Architecture, component detail, or diagrams for code that does not exist.**
- **Manual test cases the automated suites already cover.**

## Sections at a Glance

| # | Section | Holds | Source |
|---|---|---|---|
| 1 | Overview & Scope | Product, target user, value proposition, goals, out of scope | Spec |
| 2 | Setting Up | Prerequisites, env var names, install, run | Code, README, `package.json` |
| 3 | Architecture | Architecture diagram, how parts interact, key decisions | Code; `PROJECT_STATE.md` § Architecture and § Decisions |
| 4 | Components | Per module: purpose, API, dependencies, invariants, diagrams | Code, checked against spec |
| 5 | Requirements | FRs, NFRs, known limitations; user stories / use cases if enabled | Spec; `PROJECT_STATE.md` § Known Gaps |
| 6 | Glossary | Domain and project-specific technical terms | Spec, code |
| 7 | Testing | How to run automated suites; manual cases worth keeping | Code, plans |

**Where the code and the spec differ, the guide describes the code** and cites the deviation's
`D`-ID from `PROJECT_STATE.md` § Deviations.

## 1. Overview & Scope

- Two or three sentences: what the product is and the problem it solves.
- **Target user** as a short profile. **Value proposition** in one or two sentences.
- **Goals** — outcomes, not features.
- **Out of scope** — from the spec and `PROJECT_STATE.md` § Architecture and § Decisions, each with a short reason, so
  nobody re-proposes it.

## 2. Setting Up

Prerequisites with versions; each environment variable's name, purpose, and where to obtain it;
install, migrate, and run commands exactly as they work today. Confirm every command exists in
`package.json` or the README before writing it.

## 3. Architecture

- The architecture diagram, then a numbered walkthrough of one representative request.
- One short paragraph per main part: what it owns, and what it may call.
- A **Key Decisions** table:

| Decision | Why | Rejected | Ref |
|---|---|---|---|
| <what was decided> | <why — from the `C`-entry> | <the rejected alternative, only if the `C`-entry records one> | C<n> |

Include a decision only if a developer might otherwise undo it. Cite the `C`-ID so the reader can
find the full context in `PROJECT_STATE.md`.

## 4. Components

One subsection per module, depended-on modules first:

- **Purpose** — one sentence.
- **API** — the exported functions and types other code uses, linked to their files.
- **Depends on** — other components and external services.
- **Invariants** — rules it enforces that break things when violated (see `PROJECT_STATE.md` § Conventions).
- **Diagrams** — only where [diagrams.md](diagrams.md) calls for one.
- **Deviations** — where it differs from its spec, one line citing the `D`-ID.

## 5. Requirements

- **Functional requirements** — numbered as in the spec. Suffix *(planned)* until the workstream
  delivering it is `Documented`; remove the suffix at that checkpoint.
- **Non-functional requirements** — numbered; measurable wherever the spec makes them measurable.
- **Known limitations** — from `PROJECT_STATE.md` § Known Gaps, each with its reason.
- **User stories** — only if `Developer guide optional sections` includes `user stories`. Table:
  Priority (`* * *` must have · `* *` nice to have · `*` unlikely) | As a … | I want to … | So that I can ….
- **Use cases** — only if the setting includes `use cases`. Per use case: the main success
  scenario as numbered steps, then extensions (`2a.`, `2a1.` …).

## 6. Glossary

Alphabetical. Domain terms and project-specific technical terms a newcomer would not know, one or
two sentences each.

## 7. Testing

- **Automated** — for each suite: the exact command, what it covers, what it needs (a database,
  credentials), and roughly how long it takes if slow.
- **Manual** — only cases cheaper to run by hand than to automate: visual layout, browser- or
  OS-specific behaviour, third-party sign-in. Each has **Prerequisites**, **Steps**, **Expected**.

## Changes to Documented Work

- **Small changes since the last checkpoint.** Scan the Done ledger (`docs/project-state/done-ledger.md`) for
  entries after the newest `Documented` date that touch documented sections, and include the
  corrections in the proposal.
- **A documented workstream is abandoned or reversed.** Propose removing or rewriting its
  sections. The operator decides.
- **Hand edits.** Keep them. If one now contradicts the code, list it in the proposal instead of
  overwriting it.
- **The guide contradicts `PROJECT_STATE.md` outside a checkpoint.** Do not edit the guide, and do
  not change any Guide value. Follow the state file, tell the operator, and add
  `guide out of date: <what>` to the Progress cell of the workstream it concerns — or, if it
  concerns no single workstream, to § Needs a Human — so the next checkpoint fixes it.
- **Use `guide out of date` only for the guide being wrong.** Drift between `PROJECT_STATE.md` and
  the code is a reconciliation problem — correct the state file, or raise it in § Needs a Human — not a
  guide note.
