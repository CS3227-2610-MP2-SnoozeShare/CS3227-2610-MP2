# First Write

`docs/DeveloperGuide.md` does not exist and a spec has been approved. The first write is due
under every checkpoint setting. Seed the guide with what the spec settles — nothing about code.

## Steps

1. **Draft a proposal, not the file.** List what you will seed and where each part comes from —
   not the text. Nothing is created until the operator approves.
2. From the first approved spec, propose:
   - **§ 1 Overview & Scope** — which subsections you will fill, and their sources.
   - **§ 5 Requirements** — every functional requirement suffixed *(planned)*; non-functional
     requirements; known limitations from `PROJECT_STATE.md` § Known Gaps. User stories and use cases only
     if enabled in `AGENTS.md`.
   - **§ 6 Glossary** — terms the spec defines or relies on.
   - **§ 2 Setting Up** — only if setup already exists (a `package.json`, a README with working
     commands). Otherwise keep the template's placeholder.
3. **Leave § 3 Architecture, § 4 Components and § 7 Testing as the template's placeholder
   line, with no diagrams.** A spec's planned layout is not architecture yet; it becomes
   architecture when a feature is confirmed. Say this in the proposal.
4. **Propose and wait**, as in [SKILL.md](SKILL.md).
5. **On approval**, copy [guide-template.md](guide-template.md) to `docs/DeveloperGuide.md` and
   fill in the approved content.
6. **Record** in `PROJECT_STATE.md`: set the header line to
   `**Developer guide:** seeded from [spec](<path>) on YYYY-MM-DD`, and add a Done line. Guide
   column values do not change — no workstream has been documented yet.

## Bounds

- **Do not invent** goals, users, or requirements the spec does not state. If the spec is silent,
  write "Not specified" and list it as an open question in the proposal.
- **Keep the spec's requirement numbering** so the guide and spec can be cross-referenced.
- **Do not paraphrase a requirement into a different one.** Tighten wording; never change meaning.
