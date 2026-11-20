---
name: docs
description: Use when writing or changing anything in `docs/`, an `AGENTS.md`, a skill, `README.md` or `CONTRIBUTING.md`.
---

- `docs/internals/` records each architectural decision and the constraint that decided it. Before you add a paragraph, ask what a maintainer would get wrong without it. Leave out how-it-works, how-to steps and test results. A limitation, gap or workaround is a GitHub issue, never a section, and a reason the code must respect is a test. Add or change a section only for a decision the user made in chat, and name it in the PR body so the review checks it.
- `docs/internals/` has one page per area, listed in `docs/README.md`. A section's heading states the decision, and its text names the constraint and the alternative it rejected. What a feature does for the player belongs in `docs/user/`, not here. Add a page only for an area none covers, and use the words in `glossary.md`.
- `docs/user/` helps players do a task, in the mod's voice, with no implementation details or contributor tooling. Update the section when how to use a feature changes.
- `docs/operations/` holds setup, run and release procedures, for an agent to follow. A step that works around a fault is a fault: fix the script or the `mise` task instead.
- State each fact once, in the page that owns it, and link to the source instead of copying it. When a decision changes, rewrite or delete the text in the same change.
- An `AGENTS.md` holds only what every agent in its directory must know, or often needs and cannot easily find. A procedure or reference that only some tasks need is a skill in the `.claude/skills/` beside it, which that `AGENTS.md` names with when to load it. What a check enforces needs neither. A routine task, such as adding a feature, should need neither too: when it does, open an issue that moves the prose into the code or a check, and name the line it deletes.
- `CONTRIBUTING.md` holds only the policy for outside contributors and links `docs/operations/development.md` for setup, running and checks.
