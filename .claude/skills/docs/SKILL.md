---
name: docs
description: Use when writing or changing anything in `docs/`, an `AGENTS.md`, a skill, `README.md` or `CONTRIBUTING.md`.
---

- `docs/internals/` records why: each architectural decision, the constraint that decided it, and traps the source can't show. Before you add a paragraph, ask what a maintainer would get wrong without it. Leave out how-to steps, code tours, file lists and test results.
- `docs/internals/` has one page per area, listed in `docs/README.md`. A section's heading states the decision, and its text names the constraint and the alternative it rejected. What a feature does for the player belongs in `docs/user/`, not here. Add a page only for an area none covers, and use the words in `glossary.md`.
- `docs/user/` helps players do a task, in the mod's voice, with no implementation details or contributor tooling. Update the section when how to use a feature changes.
- `docs/operations/` holds setup, run and release procedures, for an agent to follow.
- State each fact once, in the page that owns it, and link to the source instead of copying it. When a decision changes, rewrite or delete the text in the same change.
- An `AGENTS.md` holds only what every agent in its directory must know, or often needs and cannot easily find. A procedure or reference that only some tasks need is a skill in the `.claude/skills/` beside it, which that `AGENTS.md` names with when to load it. What a check enforces needs neither. A routine task, such as adding a feature, should need neither too: when it does, open an issue that moves the prose into the code or a check, and name the line it deletes.
- `CONTRIBUTING.md` holds only the policy for outside contributors and links `docs/operations/development.md` for setup, running and checks.
