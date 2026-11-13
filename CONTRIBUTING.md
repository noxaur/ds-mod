# Contributing to ds-mod

Thank you for your help. This page explains what a pull request must contain, where to start reading, and how to submit a change. To set up a checkout, see the [development runbook](docs/operations/development.md).

ds-mod has a small team with a clear plan for the mod. A pull request takes more effort to review than to write. The most useful contributions fix a bug that you hit while you played. For a new feature, open an issue first.

## Rules for a pull request

### Follow the server's rules

dragsim.co bans macros, scripts and other automated actions. ds-mod shows information, and the player makes every move.

Auto-clicking is allowed under 10 CPS, and only while the player is actively moving. The `no-automation` check stops release code from sending anything for the player. If your change must send something, open an issue first.

### Fix one problem

Fix one problem in each pull request. Include every change that the fix needs across `api/`, the backend, the mod, the website, the specs, the tests and the docs. Put a cleanup, a refactor or a second fix in a separate pull request.

### Verify the bug, then show the fix

Verify a bug before you fix it. Say how you found it, how you checked the fix, and what you saw. A test, a log line or a capture is evidence. "Tests pass" is not.

For a UI change, attach a before image and an after image. For motion, attach a video. Upload them to the pull request. Don't commit them.

### Keep it fast

ds-mod runs inside the render and tick loops, so a dropped frame is a bug. For a change on those paths, measure the frame time before and after, and put both numbers in the pull request.

## Where to read first

- [AGENTS.md](AGENTS.md) has the principles, the layout and the rules, with the check that enforces each one. Each directory has its own `AGENTS.md`: [mod](mod/AGENTS.md), [backend](backend/AGENTS.md) and [web](web/AGENTS.md).
- [docs/internals/](docs/internals/) records why the code has its shape. Start with the [architecture](docs/internals/architecture.md).
- [api/openapi.yaml](api/openapi.yaml) is the contract between the mod, the backend and the website. Change it first, then the backend, then its callers.

## Submit a change

- Run `mise run check`. It must pass. When you change `mod/…/edge`, a screen or the build, also run the game tests. See [Checks](docs/operations/development.md#checks).
- Name the branch with at most three hyphenated words, such as `fix-tab-parser`.
- Write commit messages and the pull request title in English: `type(scope): description`. The scope is optional. Example: `fix(capture): keep removed tab list entries`.
- Write the pull request body in this order: the problem in one or two sentences, a summary of the change, the evidence, and the merge danger. The merge danger says if the change is easy to undo, and what else it can break.
- Keep plans, notes and captures out of the change. Captures hold player names and private chat.
