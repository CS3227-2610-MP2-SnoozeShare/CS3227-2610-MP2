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

## Trevor's Reflections
Here are some sample questions that you can use to guide your reflections.

- What tasks were the AI agent customized to perform and how to determine the appropriate skill set for each task?
- How did you define a specific skill and make sure that it is working?
- What tasks were handled effectively by the agent and help to improve productivity, code quality, or testing efficiency?
- Where did the agent require additional guidance or correction? Were there situations where using the agent created additional work rather than reducing it?
- What would you change in the agent's instructions or skill set if you repeated the task?  What additional skills or tools would make the agent more useful?
- What did you learn about designing an effective single AI agent for software engineering tasks?

