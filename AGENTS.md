# ds-mod

ds-mod is a client-side quality-of-life mod for the Dragon Simulator server (dragsim.co), in the spirit of SkyHanni. This repo holds the mod, a backend it talks to over REST, and a website.

## What makes ds-mod special

We can't compromise on these.

- Simple by default: the core works with no setup, and each advanced feature stays out of the way until the player needs it.
- Fast and smooth: the mod runs inside the render and tick loops, so a dropped frame is a bug. Measure it, and do not trade it for convenience.

## General principles

- Channel your inner DRY, YAGNI, KISS and SOLID.
- Build on the shared layer each area already has, such as the capture writer. If it can't do what you need, extend it there.
- Write real fixes, not bypasses.
- Keep code in one function unless a part is reused or composable.
- Before adding complexity for a speculative or vanishingly unlikely race or edge case, explain the concrete failure mode, likelihood and complexity cost to the user and get their buy-in.
- If a short request has two possible meanings, ask one question before you start.
- Call a bug fixed only when a test, log or capture shows it.
- A rule in prose gets skipped, so turn a repeated mistake into a check. See Rules.

## Layout

| Directory | What it is | Its rules |
| --- | --- | --- |
| `mod/` | The Fabric mod, in Kotlin, built with Gradle | `mod/AGENTS.md` |
| `backend/` | The REST service, in TypeScript with Effect, hosted with Void on Cloudflare | `backend/AGENTS.md` |
| `web/` | The website, in Next.js with Effect | `web/AGENTS.md` |
| `api/` | `openapi.json`, the REST contract between the mod, the backend and the website | API |
| `specs/` | TLA+ specs for any project | Formal specs |
| `docs/` | Guides for players and contributors, and the reasons behind the code | The `docs` skill |

"Server" means dragsim.co. Our own service is the backend. Skills sit in `.claude/skills/` beside the `AGENTS.md` that names them.

## API

- `backend/src/api.ts` defines every REST endpoint except sign-in, which Better Auth serves at `/api/auth/*`, and `api/openapi.json` is generated from it. Change the definition first, then the callers.

## Formal specs

- When you change a state machine that has a spec, change the spec first and run TLC on it, then change the code.
- Write a spec for a new feature only when it has interleaving states that a unit test can't enumerate. A parser or an overlay needs a test.

## Docs

Before you change an area, read its page in `docs/internals/`. Most code changes need no doc change. Update the page when a change alters a decision it records, or how a player uses a feature, and load the `docs` skill first.

## Rules

Every rule is enforced by a check in `mise run check`, and a failure prints the fix. When a mistake happens a second time, or before you add, change or remove a check or an exception, load the `rules` skill.

## Checks

- `mise run check` must pass, and `mise install` gives the pinned tools it needs.
- Test the real implementation instead of copying its logic into the test.
- When a CI job nears its 3 minutes, make it faster or move the slow part to the beta pipeline. Raise a limit or a perf cap only when the user agrees.

## Branches and pull requests

- Use a short branch name of at most three hyphenated words, such as `capture-writer` or `fix-tab-parser`.
- Make a PR only when asked. Before you create one, load the `pr` skill and follow it. It is mandatory.

## Code and residue

- Agents read code well, so add comments only where the code can't speak for itself.
- Keep plans, research notes and scratch files outside the worktree.
