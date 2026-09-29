---
layout: default
title: Reflections
permalink: /reflections/
---

# Reflections

## Congchen's Reflections
Here are some sample questions that you can use to guide your reflections.

- What tasks were the AI agent customized to perform and how to determine the appropriate skill set for each task?
- How did you define a specific skill and make sure that it is working?
- What tasks were handled effectively by the agent and help to improve productivity, code quality, or testing efficiency?
- Where did the agent require additional guidance or correction? Were there situations where using the agent created additional work rather than reducing it?
- What would you change in the agent's instructions or skill set if you repeated the task?  What additional skills or tools would make the agent more useful?
- What did you learn about designing an effective single AI agent for software engineering tasks?

## Nathan's Reflections
What tasks were the AI agent customized to perform and how to determine the appropriate skill set for each task?

I used Claude Code as my AI agent for building the Guest role features in SnoozeShare, specifically F1 (Listing Search & Property Discovery), F2 (Booking Execution & Trip Hub with escrow), F3 (Guest Feedback, Disputes & Reviews), and F4 (Guest Wallet Management). These mapped to workstreams W2 through W5 in our project tracker.

The agent was customized through a skills package called "superpowers" which provided structured workflows for different stages of development. The three skills I relied on most were:

1. Brainstorming: Before any code was written, this skill would walk through the requirements with me, clarify what I actually meant, explore edge cases, and make sure we were aligned on the design. It essentially forced a proper planning phase before jumping into implementation.

2. Maintaining Project State: This skill kept PROJECT_STATE.md as the single source of truth across all sessions and agents. Since our team had multiple people working across different branches and even different AI models, this was critical for making sure no context was lost between sessions.

3. Test-Driven Development: The agent followed a TDD-first approach when building each feature slice. Tests were written before the implementation code, which caught issues early and gave me confidence that the logic was sound before moving on.

Determining the right skill set came down to matching the task's complexity to the workflow. For feature-level work (like building an entire booking system), I needed the full pipeline: brainstorming, then a spec, then a plan, then TDD implementation. For smaller fixes, the agent could just go ahead and do it without all the ceremony.


How did you define a specific skill and make sure that it is working?

Each skill was defined as a markdown file in .claude/skills/ with clear instructions on when to trigger, what steps to follow, and what output to produce. The superpowers package came with pre-built skills that covered most of what I needed. To make sure they were working, I would observe whether the agent actually followed the skill's workflow during a task For example, whether it stopped to brainstorm before coding, or whether it updated PROJECT_STATE.md after completing a task. If it skipped a step, I would prompt it to follow through, and over time the agent got better at triggering the right skills without me having to remind it.


What tasks were handled effectively by the agent and helped to improve productivity, code quality, or testing efficiency?

The agent was surprisingly good at implementing business logic and functionality. After a solid brainstorming session where we aligned on the design, the agent would produce code that was very close to what I had envisioned. The booking flow with escrow, wallet transactions, and the dispute/review system all came together well through this process. The TDD approach also meant that by the time a feature was "done," it already had meaningful test coverage. This means that I did not have to go back and write tests after the fact.

Planning was another area where the agent really shone. It would produce detailed specs and implementation plans, then verify them with me before writing a single line of code. This front-loaded the thinking and reduced the amount of rework needed downstream.


Where did the agent require additional guidance or correction? Were there situations where using the agent created additional work rather than reducing it?

The biggest weakness was UI/UX work. The agent struggled with getting layouts, spacing, and visual elements right. I suspect this is because agents cannot actually see the rendered output. They are essentially working blind when it comes to how a JavaFX layout actually looks on screen. I tried pointing the agent to screenshots of the current state, and sometimes that helped, but it was inconsistent. A lot of the time, UI changes I requested were not visually reflected in the way I expected despite the code being modified. This meant I had to do more manual tweaking on the frontend side.

There were also cases where corrections were needed even after the brainstorming phase. In hindsight, some of this was probably a prompting issue on my end. I may not have been specific enough about certain details, and the agent filled in the gaps with its own assumptions. Getting better at writing precise prompts is something I would focus on if I did this again.


What would you change in the agent's instructions or skill set if you repeated the task? What additional skills or tools would make the agent more useful?

If I repeated this, I would invest more in tooling for UI work. The agent's inability to see rendered layouts was a consistent pain point. Something like a skill that takes a screenshot of the current UI, compares it to a mockup, and identifies the differences would go a long way. Even a simple feedback loop where the agent runs the app, captures the window, and checks its own work would help.

I would also refine my own prompting habits. The agent is only as good as the instructions it receives, and I think I left too much ambiguity in some of my requests early on. Being more explicit about expected behaviour upfront, especially for visual elements that I found the most problems with would reduce the back-and-forth.


What did you learn about designing an effective single AI agent for software engineering tasks?

The biggest lesson was that planning matters more than coding speed. In past projects where I jumped straight into implementation with the agent, I ended up with a lot of corrections and lower quality output. The brainstorming skill in this project forced a proper design conversation before any code was written, and while it used up more context window, the output was significantly better.

Agents are naturally inclined towards action and they want to start writing code immediately. A well-designed skill that slows them down and makes them think first is genuinely valuable, even if it feels like overhead in the moment. The context spent on planning pays for itself many times over in reduced rework.

The other insight is that agents excel at structured, logic-heavy tasks but struggle with anything that requires visual judgement. Knowing this boundary helps you allocate work more effectively. Let the agent handle the service layer, the data access, the business rules, and the tests, but expect to do more hands-on work for anything the user actually sees.


### Supporting Evidence from Project Artifacts

The following project files illustrate the points made above.

**Brainstorming and planning before code:**
- `docs/superpowers/specs/2026-09-24-w2-listing-search-design.md` — the W2 spec header reads "Approved via brainstorming session, 2026-09-24", showing design was validated before implementation began. Decision W2-D2 in the same file records a requirement I revised during spec review ("guests should still see unavailable listings for awareness, but available ones take priority"), demonstrating how brainstorming caught design issues before any code was written.

**TDD-first workflow:**
- `docs/superpowers/plans/2026-09-24-w2-listing-search.md` — Task 1 explicitly follows a red-green-refactor cycle: "Step 1: Write tests for SearchCriteria", then "Step 2: Run test to verify it fails" with the expected compilation failure documented upfront.
- `docs/superpowers/plans/2026-09-26-w4-guest-feedback-disputes-reviews.md` — Task 1, Step 4 reads "Write the failing tests for `fileTicket()`" before any implementation code, with 8 validation rules specified in the design spec (`docs/superpowers/specs/2026-09-26-w4-guest-feedback-disputes-reviews-design.md`, § 3.1) and all 8 implemented exactly as spec'd.

**UI/UX struggles and screenshot-driven corrections:**
- `logs/2026-09-26_22-10-00_w8.md` — I had to attach screenshots of the wallet page and booking modals to get padding and layout consistent, because the agent could not see the rendered output itself.
- `logs/2026-09-27_01-57-00_w9.md` — wallet UI required mockup screenshots for top-up and withdrawal modals; the agent implemented the layout but the CSS was overridden by shell-level styles that only became visible after I ran the app and verified.
- `logs/2026-09-27_12-32-00_host-ui-touchup.md` and `logs/2026-09-29_00-58-00_host-ui-touchup.md` — multiple rounds of UI touch-up sessions were needed to correct visual issues the agent could not detect on its own.

**PROJECT_STATE.md as cross-session source of truth:**
- `PROJECT_STATE.md`, § How to Resume — the sessions table tracked 8+ concurrent sessions across different agents (Claude Opus, Claude Sonnet, Codex) on different branches, ensuring no context was lost when switching between sessions or models.

**Planning reducing rework:**
- `docs/project-state/done-ledger.md` — W2 through W5 each have clean single entries in the done ledger ("All 4/9/8/6 tasks complete") with no follow-up rework entries, compared to later workstreams like W6 which required multiple refinement rounds. This suggests the upfront spec and plan investment paid off in fewer surprises during implementation.

## Trevor's Reflections
Here are some sample questions that you can use to guide your reflections.

- What tasks were the AI agent customized to perform and how to determine the appropriate skill set for each task?
- How did you define a specific skill and make sure that it is working?
- What tasks were handled effectively by the agent and help to improve productivity, code quality, or testing efficiency?
- Where did the agent require additional guidance or correction? Were there situations where using the agent created additional work rather than reducing it?
- What would you change in the agent's instructions or skill set if you repeated the task?  What additional skills or tools would make the agent more useful?
- What did you learn about designing an effective single AI agent for software engineering tasks?

