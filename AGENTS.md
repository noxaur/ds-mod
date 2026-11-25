# ds-mod

ds-mod is a client-side quality-of-life mod for the Dragon Simulator server (dragsim.co). This repo holds the mod, a backend it talks to over REST, and a website.

## What makes ds-mod special

We can't compromise on these.

- Simple by default: the core works with no setup, and each advanced feature stays out of the way until the player needs it.
- Fast and smooth: the mod runs inside the render and tick loops, so a dropped frame is a bug. Measure it, and do not trade it for convenience.

## Layout

| Directory | What it is | Its rules |
| --- | --- | --- |
| `mod/` | The Fabric mod, in Kotlin, built with Gradle | `mod/AGENTS.md` |
| `backend/` | The REST service, in TypeScript with Effect, hosted with Void on Cloudflare | `backend/AGENTS.md` |
| `web/` | The website, in Next.js with Effect | `web/AGENTS.md` |
| `api/` | `openapi.json`, the REST contract between the mod, the backend and the website | API |
| `specs/` | TLA+ specs for any project | Formal specs |
| `scripts/` | Checks that span every project | Checks |
| `docs/` | Guides for players and contributors, and the reasons behind the code | Docs |

"Server" means dragsim.co. Our own service is the backend.

## API

- `backend/src/api.ts` defines every REST endpoint except sign-in, which Better Auth serves at `/api/auth/*`, and `api/openapi.json` is generated from it. Change the definition first, then the callers.

## Formal specs

- When you change a state machine that has a spec, change the spec first, then the code.
- Write a spec for a new feature only when it has interleaving states that a unit test can't enumerate. A parser or an overlay needs a test.

## Docs

Before you change an area, read its page in `docs/internals/`. Most code changes need no doc change. Update the page when a change alters a decision it records, or how a player uses a feature.

## Checks

- A commit runs the docs and comment checks and the checks of every project its staged change reaches, and a pull request runs every project's checks and the game tests its change reaches.
- Test the real implementation instead of copying its logic into the test.
- When a CI job warns that it took over its 3 minutes, make it faster or move the slow part to the beta pipeline. Raise a limit or a perf cap only when the user agrees.

## Branches and pull requests

- Never make a PR unless the user explicitly asks for one.
- Use a short branch name of at most three hyphenated words, such as `capture-writer` or `fix-tab-parser`.
- One request is one PR, with every change it needs across `api/`, the projects, the tests and the docs. Split only when asked. Outside contributions follow [CONTRIBUTING.md](CONTRIBUTING.md#fix-one-problem).
- Conventional commit titles, plain language: `fix(capture): keep removed tab list entries`.
- Body: the problem in a sentence or two, then how you fixed it, then the evidence: the test, log or capture that shows it works, and the numbers when the change touches a hot path. "Tests pass" is not evidence. Name any decision you changed in `docs/internals/`, so the review checks it. End with the model and harness that did the work.
- UI changes need before/after screenshots; motion or timing needs a short video. Upload them to the PR, never commit them.
- Merge `main` into the branch and fix the conflicts yourself.
- When watching a PR, verify each bot finding against the source, fix the real ones, and dismiss false positives with a written reason. Stay quiet when nothing is new, and stop once the checks and bots are green on the latest commit.

## Code and residue

- Keep plans, research notes and scratch files outside the worktree.

## Principles

- Write code that is DRY, YAGNI, KISS and SOLID.
- Reuse the shared layer each area has, such as the capture writer. When it falls short, grow it rather than writing around it.
- Solve the real problem. A bypass is not a fix.
- One function holds the code until a piece of it is reused or composes with others.
- A rare race or edge case earns extra complexity only with the user's agreement. First tell them how it would fail, how often, and what handling it costs.
- If a short request reads two ways, ask one question before starting.
- A bug counts as fixed once a test, log or capture proves it.
- When a mistake repeats, add a check that fails with the fix in its message. A sentence in a doc won't stop it.
