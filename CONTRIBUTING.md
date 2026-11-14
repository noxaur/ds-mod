# Contributing

Thank you for being interested in developing ds-mod. If you want to contribute in other ways than GitHub, check out the [Discord](https://discord.gg/YUZCYAnr7F).

## Send us data

I'm just a normal player, and I haven't reached every phase of the game. A great way to help is to send us data from the parts I haven't seen yet. See the Discord for how.

## Report a bug

Open an issue. Tell us what you did, what you expected and what happened instead. Post ideas for features in the Discord.

## What we won't take

- Anything that plays for you. The server bans macros and scripts. Auto-clicking is fine under 10 CPS, only while you're moving.
- A new dependency we didn't agree on first.
- A Minecraft version bump. The server decides the version.

## Rules for a pull request

### Fix one problem

Fix one problem in each pull request. Include every change the fix needs across `api/`, the backend, the mod, the website, the tests and the docs. Put a cleanup, a refactor or a second fix in its own pull request.

A small fix for an obvious bug needs no issue. For a bigger fix, link the issue that shows the bug.

### Show that it works

Verify a bug before you fix it. Say how you found the bug, how you checked the fix and what you saw. A test, a log or a capture counts. "Tests pass" doesn't.

For a UI change, attach before and after screenshots. Add a short video when the change is about motion or timing. Upload them to the pull request. Don't commit them.

### Keep it fast

ds-mod runs inside the render and tick loops, so a dropped frame is a bug. If your change touches a hot path, measure it, and put the numbers in the pull request.

## Submit a change

- Name the branch with at most three hyphenated words, such as `fix-tab-parser`.
- Write commit messages and the pull request title in English: `type(scope): description`. The scope is optional. Example: `fix(capture): keep removed tab list entries`.
- Write the pull request body in this order:
  1. The problem, in one or two sentences.
  2. A summary of the change. Use the smallest view that keeps it clear, such as a file tree, pseudocode or a diff.
  3. The evidence.
  4. The merge danger: whether the change is easy to undo, and what else it can break.
  5. If an AI wrote any of it, the model and what it did, such as `Claude Opus 5.5 for implementation.`
- Merge `main` into your branch and fix the conflicts yourself.
- Keep plans and notes out of the change.

## Development

See the [development docs](docs/operations/development.md).
