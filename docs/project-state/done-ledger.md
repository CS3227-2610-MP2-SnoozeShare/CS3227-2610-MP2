# Done Ledger — SnoozeShare

The full chronological record of every change, newest first. One line each: what changed and
why, with a commit, plan or artifact as the reference. Not a copy of `git log`.

| Date | What changed | Workstream | Ref |
|---|---|---|---|
| 2026-09-22 | Built and populated the shared team-reference mock DB at `db/snoozeshare-mock.db` by running `db/schema.sql` + `db/seed-mock-data.sql` (operator approved). Verified with `PRAGMA foreign_key_check` (clean) and sample queries (pending-requests-by-host, wallet statement). Excepted it from the `*.db` `.gitignore` rule (that rule is for the app's own local runtime DB, not this committed reference file) | — | `db/snoozeshare-mock.db`, `.gitignore` (uncommitted) |
| 2026-09-22 | Removed the guessed guest-side service fee entirely (only the host's 3% payout fee remains); confirmed booking rejection/host-cancellation = 100% refund, single-sided ledger entries for ticket remedies/agent overrides, and currency=SGD as real decisions (C7–C10). Recomputed every booking total and every guest wallet balance in `db/seed-mock-data.sql`; updated `db/schema.sql` and the architecture proposal to match. Re-validated against a scratch SQLite DB | — | `db/schema.sql`, `db/seed-mock-data.sql`, `docs/SnoozeShare-Architecture-Proposal.md` (uncommitted) |
| 2026-09-22 | Corrected `db/schema.sql`/`db/seed-mock-data.sql` `properties` table to the operator-specified House field list (propertyType/amenities enums, streetAddress/region/postalCode, maxGuests/bedrooms/bathrooms, checkInTime/checkOutTime), replacing an earlier guessed field set; also updated the architecture proposal's properties schema note. Re-validated against a scratch SQLite DB — all FK/type checks pass | — | `db/schema.sql`, `db/seed-mock-data.sql`, `docs/SnoozeShare-Architecture-Proposal.md` (uncommitted) |
| 2026-09-22 | Drafted `db/schema.sql` (10-table DDL) and `db/seed-mock-data.sql` (16 users, 10 properties, 15 bookings covering all statuses, 13 wallets, 35 wallet_transactions covering all types, 6 tickets covering all statuses, 3 reviews, 10 availability blocks, 15 audit log rows) for a shared team-reference mock DB. Validated by loading into a scratch SQLite file — schema/seed load cleanly, wallet balances reconcile to transaction sums, zero FK orphans. Not yet loaded into a real shared `.db` — awaiting operator approval | — | `db/schema.sql`, `db/seed-mock-data.sql` (uncommitted) |
| 2026-09-22 | Switched embedded DB from H2 to SQLite (`org.xerial:sqlite-jdbc`) per operator decision, resolving Q2/D1; updated `build.gradle` and `.gitignore` | — | uncommitted (working tree) |
| 2026-09-22 | Bootstrapped `PROJECT_STATE.md` from the architecture proposal, product backlog, and git history — no code changed (inferred/backfilled entry, not a commit) | — | `PROJECT_STATE.md` |
| 2026-09-22 (uncommitted, staged) | Added `AGENTS.md`/`CLAUDE.md` and the `maintaining-project-state` / `update-documentation` skills to the repo | — | staged, not yet committed |
| unknown date (inferred from commit) | Added base Gradle project layout: package skeleton under `com.snoozeshare.*`, placeholder `Main`/`Launcher`, checkstyle config, `.claude/launch.json` | — | `d53c621` |
| unknown date (inferred from commit) | Restructured `docs/ProductBacklog.md` against the architecture proposal — added F0/F10/F11 epics, fixed sprint sequencing, terminology alignment (Ledger → Wallet/Transaction) | — | `9764256` |
| unknown date (inferred from commit) | Initial project scoping — added `docs/SnoozeShare-Architecture-Proposal.md` and related docs (inferred; commit message only, contents not diffed for this backfill) | — | `025978a` |
| unknown date (inferred from commit) | Initial commit | — | `94a403f` |

**Archive:** none yet — start `archive-YYYY-QN.md` beside this file past ~100 entries.
