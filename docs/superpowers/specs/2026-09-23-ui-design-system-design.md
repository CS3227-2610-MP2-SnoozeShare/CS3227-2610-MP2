# SnoozeShare UI Design System — Design Spec

**Status:** Validated against a working mockup (approved via interactive review, see § Visual Reference). Written after the fact, per operator request — see § 0.

**Author:** Claude Sonnet 5, with the operator, 2026-09-23
**Related workstream:** none yet (W1–W12) — this is cross-cutting UI foundation work that will be consumed by every future UI-touching workstream.
**Visual reference:** [SnoozeShare Fall Light UI](https://claude.ai/artifact/PWBCxbfv9e9FGVvY6RKUwd) — a Claude Design canvas, 31 artboards, shared as "anyone with the link." Treat this spec as the written record of what that canvas already shows; where the two ever disagree, the canvas is more current (it was reviewed and iterated live) and this file's § Deviations should be updated by whoever finds the drift.

---

## 0. How this spec came to exist (process note)

Per `AGENTS.md` §3, UI work of this scope is a "Big" change and normally gets a design spec *before* any artifacts are produced. That process started correctly (brainstorming: clarifying questions on theming mechanism, density, shell layout, card style, typography, scope — all answered and recorded) but the operator then explicitly redirected from "spec + HTML mockup files" to "build it live in Claude's Design canvas, write the spec after it's validated." That is recorded as decision **C11** in `PROJECT_STATE.md` § Architecture 4.2. This document is that deferred spec, written once the canvas had gone through three rounds of operator review and revision — so it documents validated decisions, not proposals awaiting approval.

---

## 1. Goals & Constraints

- **Stack:** JavaFX 25 + [AtlantaFX](https://github.com/mkpaz/atlantafx) (`io.github.mkpaz:atlantafx-base`), `PrimerLight` as the structural base theme.
- **Aesthetic goal:** "web app-esque," not a traditional native desktop look — informed by Airbnb (search bar, property cards), Linear/Notion (tab shell), and GitHub Primer (AtlantaFX's own reference aesthetic).
- **Palette:** operator-supplied "Fall Light" theme (warm cream canvas, rust sienna accent, harvest olive/amber/burgundy semantic colors) — see § 2.1. Values are taken as-is from the operator's original SCSS; this spec only changes *how* they reach the app, never the values.
- **No dark mode** in this pass (explicit decision — the palette is Fall Light only; AtlantaFX supports a dark variant structurally, so this is deferred, not blocked).
- **No new fonts** — platform-default sans-serif system stack, not Inter (see § 4).

---

## 2. Design Tokens

### 2.1 Color

Ported directly from the operator's Fall Light SCSS onto AtlantaFX's own `-color-*` variable names — the SCSS forwards the exact same names, so this is a literal port, not a reinterpretation. All contrast ratios the operator annotated (7:1+ on cream) carry over unchanged.

| Role | AtlantaFX variable | Value |
|---|---|---|
| Canvas | `-color-bg-default` | `#fdf8f0` |
| Canvas (cards/overlays) | `-color-bg-overlay` | `#ffffff` |
| Canvas inset | `-color-bg-inset` / `-color-bg-subtle` | `#f0e8d8` |
| Text | `-color-fg-default` | `#100604` |
| Text muted | `-color-fg-muted` | `#381a10` |
| Text subtle | `-color-fg-subtle` | `#6c4434` |
| Border | `-color-border-default` | `#c0a080` |
| Border muted | `-color-border-muted` | `#d8c4a8` |
| Accent (brand) | `-color-accent-emphasis` / `-color-accent-fg` | `#98300c` |
| Accent subtle (fill) | `-color-accent-subtle` | `#fde8d8` |
| Success | `-color-success-emphasis` / `-fg` | `#40680c` |
| Success subtle | `-color-success-subtle` | `#e8f8d0` |
| Warning | `-color-warning-emphasis` / `-fg` | `#7e5400` |
| Warning subtle | `-color-warning-subtle` | `#fff4d8` |
| Danger | `-color-danger-emphasis` / `-fg` | `#980c1c` |
| Danger subtle | `-color-danger-subtle` | `#fcd8da` |

Plus the full `-color-{accent,success,warning,danger,base}-0..9` scales from the operator's snippet, forwarded unchanged.

**Component-specific pattern, confirmed during review:** a prominent balance/summary card (see Wallet, § 6) uses `bg-inset` + `fg-muted`, **not** a solid `accent-emphasis` fill with white text as originally drafted — the operator downgraded it during live review because the bold accent fill read as too loud for a resting-state balance display. Treat `bg-inset` + `fg-muted` as the default "highlighted info card" pattern; reserve the solid `accent-emphasis` fill (white text) for actively dangerous or high-stakes confirmations (e.g. the Dispute/Booking-decision confirm modals' summary blocks, which intentionally stay bold).

### 2.2 Structural tokens

These don't exist in the operator's SCSS (it's colors-only) — they were added to reach the "Spacious/Soft" density the operator picked in a visual review (over "Dense/Flat" and a middle "Balanced" option), since AtlantaFX's real defaults are `border-radius: 4px`, `padding: 8px/12px`, no shadow.

| Token | Value | Used for |
|---|---|---|
| `radius-sm` | 8px | inputs, buttons, badges, table rows |
| `radius-md` | 12px | cards, dialogs, modals |
| `radius-pill` | 999px | status badges, chips, search-bar segments |
| `space-1..6` | 4 / 8 / 12 / 16 / 20 / 24px | paddings/gaps |
| `shadow-card` | `0 4px 14px rgba(16,6,4,.08)` | cards, popovers, non-modal elevation |
| `shadow-modal` | `0 20px 60px rgba(16,6,4,.35)` | modal dialogs only (stronger, since modals sit over a dimmed/blurred backdrop) |

### 2.3 Typography

Platform-default sans-serif (`-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif` as the web-mockup stand-in; in JavaFX this is simply AtlantaFX's real default — `sans-serif` — i.e. whatever `-fx-font-family` the OS resolves it to). See § 4 for why Inter was considered and rejected.

| Style | Size / weight | Used for |
|---|---|---|
| Title 1 | 28px / 800 | screen titles |
| Title 2 | 20px / 700 | section headers |
| Title 3 | 15px / 700 | card titles |
| Body | 13px / 400 | property descriptions, table cells |
| Small | 11px / 400 | captions, meta |

---

## 3. Theming Mechanism

AtlantaFX exposes every component color as a CSS custom property (`-color-*`) rather than a literal hex — this is how the runtime-override approach works at all, and it's why the operator's SCSS variable names map 1:1 onto it.

1. Add `io.github.mkpaz:atlantafx-base` to `build.gradle`.
2. At startup: `Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet())` — Primer chosen as the base because it's the most widely documented AtlantaFX theme and, structurally, the closest relative to a "web app" look; its actual colors don't matter since they're fully overridden in the next step.
3. Add a **second** stylesheet, `fall-light-theme.css`, to every `Scene` (`scene.getStylesheets().add(...)`, after the base theme). It has two parts:
   - **A `.root { -color-*: ...; }` block** — the full color table from § 2.1. This alone repaints every AtlantaFX control correctly, since components reference these variables, not literal hex.
   - **Explicit structural rules** for the handful of component classes needed to reach "Spacious/Soft": `.button`, `.toggle-button`, `.text-field`, `.combo-box`, `.card`, `.dialog-pane` get `-fx-background-radius`/`-fx-border-radius` bumped to `radius-sm`/`radius-md`, padding bumped, and `.card`/`.dialog-pane` get the shadow token.

**Why two parts, and why this is verified, not assumed:** I checked AtlantaFX's actual source (`mkpaz/atlantafx`, `styles/src/`) rather than trusting memory. Two things that changed the plan as a result:
- `styles/src/settings/_config.scss` defines `$border-radius: 4px`, `$padding-x: 12px`, etc. as **plain Sass variables**, compiled to fixed pixel values in the shipped CSS — they are *not* exposed as runtime CSS custom properties the way colors are. So radius/padding cannot be overridden with one clean variable declaration; the override stylesheet needs the explicit per-selector rules in step 3 above. This is fully achievable, just more verbose than the color half.
- `atlantafx-base` ships **no bundled font** (the Inter `.otf` files I initially assumed existed are only in AtlantaFX's own `sampler` demo module, not in the library dependency). Its real default is `$font-family-sans-serif: sans-serif`. This confirmed the "platform default, no bundling" decision in § 4 rather than contradicting it.

**Fallback, if this ever proves insufficient:** a full Sass rebuild — forking `styles/src/`, dropping in the operator's original SCSS verbatim (it's already written for exactly this), and compiling with dart-sass. This is what AtlantaFX's own customization docs describe as the "proper" path; the override approach above was chosen instead because it needs no new build tooling in a Java/Gradle project that has none today. Recorded as a fallback, not a rejected option, in case the CSS-override approach hits a visual gap in some AtlantaFX component that references a `base-N` shade directly instead of a semantic variable.

---

## 4. Typography decision detail

Two decisions, both made after checking claims rather than assuming them:

- **Platform default over Inter.** AtlantaFX's real default is the OS sans-serif (Segoe UI / San Francisco / Ubuntu depending on platform), not Inter — see § 3. Bundling Inter ourselves was considered (cross-platform consistency, useful for a graded demo) but rejected: it's real added work (font files + license + `Font.loadFont()` + a bundled asset to maintain) for a marginal visual gain over what AtlantaFX already gives for free.
- **No icon font decision made yet.** Ikonli (the icon library AtlantaFX's own CSS has some support for, per `$font-icon-selector` in `_config.scss`) never came up during review. The mockups use inline Unicode glyphs (‹ › ✕ ★ ▾) as stand-ins. **Open question for whoever writes the implementation plan:** pick an icon set (Ikonli + a specific pack, e.g. Feather or Material) or continue without one.

---

## 5. App Shell & Navigation

Single `BorderPane` per role window: top bar → `TabLine` (AtlantaFX's real tab-bar control, not hand-rolled) → content region swapped via `SceneRouter`.

| Role | Tabs (final, 4 each) |
|---|---|
| Guest | Search · Trips · Messages · **Wallet** |
| Host | Listings · Requests · Messages · **Wallet** |
| Agent | Disputes · Accounts · Audit Log · **Categories** |

Wallet was added as a persistent 4th tab, shared Guest/Host (like Messages), after the operator asked for it explicitly — it replaced the original plan of a wallet-balance-pill-opens-a-panel pattern, which was never actually built as a full screen.

**Sub-page breadcrumb pattern** (Host Listing Detail/Form/Calendar, Agent Dispute Detail): a plain text line — `Parent › Current page` with the current page bold — sitting directly under the tab row, inside the content padding area. **Not** a separate bordered/backgrounded row; an earlier draft did that and the operator explicitly rejected it for visually competing with the tab row. The parent tab (e.g. "Listings") stays visually active/bold even while on a sub-page of it.

**Modal pattern:** every dialog (Listing Detail, New Ticket, Leave a Review, all confirmation modals) renders as a centered card over a dimmed + blurred backdrop of the page behind it — mapped to AtlantaFX's `ModalPane`/`ModalBox` (in-window, no separate `Stage`). No browser-style route change; the "page" is conceptually still the same screen.

---

## 6. Component Library

Mapped to real AtlantaFX controls where they exist, so the implementation doesn't reinvent what's already shipped:

| Need | AtlantaFX control | Notes |
|---|---|---|
| Tab navigation | `TabLine` | shell nav, § 5 |
| Cards (property cards, dashboard tiles, wallet balance) | `Card` | first-class control |
| Modal dialogs | `ModalPane` / `ModalBox` | § 5 |
| Toasts / inline alerts | `Notification`, `Message` | e.g. "Booking confirmed" |
| Search input w/ icon | `CustomTextField` | leading/trailing graphic support |
| Status pills | styled `Label` + `radius-pill` | AtlantaFX has no dedicated badge control |
| On/off toggles (listing active/inactive, category active/inactive) | `ToggleSwitch` | first-class control |
| Breadcrumb | plain text, **not** the `Breadcrumbs` control | see § 5 — the operator's pattern is simpler than AtlantaFX's control (no per-segment click affordance shown in the mockups); revisit if that's wanted later |
| Filter chips / density toggle | `ToggleButton` group / `SegmentedControl` | |
| Tables (requests, audit log, transactions, accounts) | `TableView` styled via tokens | no AtlantaFX replacement |
| Dropdown / `<select>` equivalent | `ComboBox`, custom-skinned | see below |
| Chat bubbles (Messages, Dispute Detail guest/host panels) | custom composite, no AtlantaFX equivalent | § 6.1 |

### 6.1 Dropdown (`ComboBox`)

Documented and reviewed as its own pattern (operator asked to see both states explicitly):
- **Closed:** value + a right-aligned `▾` chevron, `radius-sm` corners, `border-default`.
- **Open:** trigger gets `border-accent` and squares off its bottom corners; the option list drops directly below with matching side/bottom radius, `border-accent`, `shadow-card`; the currently-active option is highlighted with `accent-subtle` fill + `accent` text and bold weight; other options are plain `fg-default`.

In JavaFX this is a skinned `ComboBox` (AtlantaFX doesn't special-case this — it's a straightforward CSS skin using the tokens above, not a new control).

### 6.2 Chat bubbles

Used in three places: Messages (Guest/Host), Agent Dispute Detail's guest/host panels. Pattern: the *viewing user's own* messages are right-aligned with `accent-subtle` fill; the other party's are left-aligned with `bg-inset` fill. (An earlier draft had this backwards — self on the left — and the operator corrected it; this is now the canonical rule, not a one-off fix.) Each bubble carries a small muted timestamp. The Dispute Detail variant additionally has its own reply input + Send button per panel (it's a full mini-messages view, not a read-only excerpt) and a role tag (e.g. "Opened ticket") next to whichever party actually raised the ticket.

---

## 7. Screen Inventory (31 artboards)

All screens live in the canvas at the link in the header; this table is the index, grouped by role, with the backlog item(s) each one satisfies (see § 8 for the full traceability pass).

| # | Screen | Role | Backlog |
|---|---|---|---|
| — | Design System reference sheet | — | (this spec's visual source) |
| 0 | Login / Sign up (role picker, Host+Agent registration code) | shared | F0.1 |
| 1 | Search (Airbnb-style bar: location/dates/guests/budget) | Guest | F1.1 |
| 2 | Listing Detail (modal; full fields, host profile, house rules, expandable reviews, booking widget) | Guest | F1.2, F2.1 |
| 3 | Trip Hub (Upcoming/Active/Past, Message host, Cancel) | Guest | F2.2, F2.3 |
| 4 | Cancel Booking (confirm, refund policy shown) | Guest | F2.3 |
| 5 | Messages (shared list+chat; New Ticket in the booking chat's own header) | Guest/Host | F3.1 (filing), general messaging |
| 6 | New Ticket (modal; category/title/description/remedy/supporting text) | Guest | F3.1 |
| 7 | Leave a Review (modal; star rating + comment) | Guest | F3.1.3 |
| 8 | Wallet (shared; balance, statement, top-up, withdraw) | Guest/Host | F4, F8 |
| 9 | Top Up (confirm modal) | Guest/Host | F4.1.1, F8.1.2 |
| 10 | Withdraw (confirm modal) | Guest/Host | F4.2.1, F8.2.1 |
| 11 | Listings (toggle active/inactive, Open Booking Calendar) | Host | F5, F6.1 (entry point) |
| 12 | Booking Calendar (month nav, block dates) | Host | F6.1 |
| 13 | Block Dates (confirm modal; large date range) | Host | F6.1.1 |
| 14 | New/Edit Listing (two-column, sectioned form) | Host | F5.1 |
| 15 | Listing Detail (read-only + reviews + performance) | Host | F5, F1.2.1 (host-side) |
| 16 | Requests (pending queue + net earnings + guest rating + past history) | Host | F7.1 |
| 17 | Approve Booking (confirm modal) | Host | F7.1.2 |
| 18 | Reject Booking (confirm modal) | Host | F7.1.2 |
| 19 | Dispute Queue | Agent | F9.1.1 |
| 20 | Dispute Detail (booking summary, guest/host mini-chats, notes, remedy actions, state-override actions) | Agent | F9.1, F9.2, F7.2.2 |
| 21 | Accept Dispute (confirm modal) | Agent | F9.1.2, F9.2.2 |
| 22 | Reject Dispute (confirm modal) | Agent | F9.1.2 |
| 23 | Manual Wallet Adjustment (confirm modal; preset + custom) | Agent | F9.2.2 |
| 24 | Force Cancel Booking (confirm modal) | Agent | F9.2.1 |
| 25 | Force Complete Booking (confirm modal) | Agent | F9.2.1 |
| 26 | Accounts (suspend/reactivate, suspension reason) | Agent | F10.1.1 |
| 27 | Suspend Account (confirm modal) | Agent | F10.1.1 |
| 28 | Audit Log (filters: User ID / Booking ID / Action Type) | Agent | F11.1 |
| 29 | Ticket Categories (list, toggle active, add) | Agent | F9.3.1 |

---

## 8. Grounding: verified against the real schema, not assumed

Every field shown anywhere in the canvas was checked against `db/schema.sql` and `docs/SnoozeShare-Architecture-Proposal.md` §4, not invented. Two gaps surfaced this way — both already recorded as **D2** in `PROJECT_STATE.md` § Deviations, repeated here because they're load-bearing for whoever builds the Accounts and Audit Log screens:

1. **`users` has no `suspensionReason` column.** The Accounts screen shows one anyway (operator-requested); it needs to be added to the schema (or sourced from an `audit_log` snapshot) before that UI can be backed by real data.
2. **`audit_log` has no literal `status`/`reason`/`amount` columns** — it's generic (`actorUserId`, `actionType`, `entityType`, `entityId`, `beforeState`, `afterState`, `timestamp`). The Audit Log screen's Status/Reason/Amount columns are **derived from each entry's `beforeState`/`afterState` JSON snapshot** (e.g. a `wallet_transactions` snapshot has `amount`; a `tickets` snapshot has `status` + `resolutionReason`). This needs a small projection layer, not a schema change.

One correction made along the way, not a gap but worth recording since it silently fixes a spec-vs-mockup mismatch: **Host registration also requires a registration code**, not just Agent (`ProductBacklog.md` F0.1.1) — the Login screen was initially built Agent-only and corrected.

---

## 9. Out of Scope / Deferred

- **Dark mode** — not designed this pass (§ 1).
- **Icon set** — not chosen (§ 4); mockups use inline glyphs as placeholders.
- **Full Sass rebuild of the AtlantaFX theme** — kept as a documented fallback (§ 3), not pursued unless the CSS-override approach hits a real gap.
- **Pixel-exact FXML layout** — this spec and the canvas establish the system and every screen's structure/content, not exact JavaFX layout-pane trees; that's implementation-plan-level detail (`writing-plans` next).

---

## 10. Self-Review

- **Placeholders:** none left — every section has concrete values, not TBDs.
- **Internal consistency:** the balance-card pattern correction (§ 2.1) and chat-bubble-alignment correction (§ 6.2) are stated as the *current* canonical rule, not flagged as open contradictions — the canvas itself only shows the corrected version.
- **Scope:** this spec covers the full design system + full screen inventory in one document, matching how it was actually built (one continuous canvas, not per-screen specs). It's large but not mixed-purpose — everything in it is "what the UI is," nothing is "how to build it in FXML," which is the right split against the next `writing-plans` step.
- **Ambiguity:** the two open items (icon set, exact override-stylesheet load point — `Scene.getStylesheets()` per scene vs. a single shared mechanism) are called out explicitly in §§ 3–4 as decisions for the implementation plan, not left implicit.
