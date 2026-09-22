# Done Ledger — SnoozeShare

The full chronological record of every change, newest first. One line each: what changed and
why, with a commit, plan or artifact as the reference. Not a copy of `git log`.

| Date | What changed | Workstream | Ref |
|---|---|---|---|
| 2026-09-22 | Switched embedded DB from H2 to SQLite (`org.xerial:sqlite-jdbc`) per operator decision, resolving Q2/D1; updated `build.gradle` and `.gitignore` | — | uncommitted (working tree) |
| 2026-09-22 | Bootstrapped `PROJECT_STATE.md` from the architecture proposal, product backlog, and git history — no code changed (inferred/backfilled entry, not a commit) | — | `PROJECT_STATE.md` |
| 2026-09-22 (uncommitted, staged) | Added `AGENTS.md`/`CLAUDE.md` and the `maintaining-project-state` / `update-documentation` skills to the repo | — | staged, not yet committed |
| unknown date (inferred from commit) | Added base Gradle project layout: package skeleton under `com.snoozeshare.*`, placeholder `Main`/`Launcher`, checkstyle config, `.claude/launch.json` | — | `d53c621` |
| unknown date (inferred from commit) | Restructured `docs/ProductBacklog.md` against the architecture proposal — added F0/F10/F11 epics, fixed sprint sequencing, terminology alignment (Ledger → Wallet/Transaction) | — | `9764256` |
| unknown date (inferred from commit) | Initial project scoping — added `docs/SnoozeShare-Architecture-Proposal.md` and related docs (inferred; commit message only, contents not diffed for this backfill) | — | `025978a` |
| unknown date (inferred from commit) | Initial commit | — | `94a403f` |

**Archive:** none yet — start `archive-YYYY-QN.md` beside this file past ~100 entries.
