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
- Follow the server's rules: no macros, scripts or other automated actions. Auto-clicking is allowed under 10 CPS, and only while the player is actively moving. The server's owner supports DragSim Utils, so a feature that mod has may send or block input for the player, and no other feature may.
- A rule in prose gets skipped, so turn a repeated mistake into a check. See Rules.


## Layout

| Directory | What it is | Its rules |
| --- | --- | --- |
| `mod/` | The Fabric mod, in Kotlin, built with Gradle | `mod/AGENTS.md` |
| `backend/` | The REST service, in Go | `backend/AGENTS.md` |
| `web/` | The website, in Next.js with Effect | `web/AGENTS.md` |
| `api/` | `openapi.yaml`, the REST contract between the mod, the backend and the website | Below |
| `specs/` | TLA+ specs for any project | Formal specs |
| `docs/` | Guides for players and contributors, and the reasons behind the code | Docs |

"Server" means dragsim.co. Our own service is the backend.

## API

- `api/openapi.yaml` is the source of truth for every REST endpoint. Change it first, then the backend, then its callers.

## Formal specs

- TLA+ specs live in `specs/`. A spec is `Name.tla` with `Name.cfg` beside it. `mod/`'s Gradle build runs TLC on all of them.
- When you change a state machine that has a spec, change the spec first and run TLC on it, then change the code.
- Write a spec for a new feature only when it has interleaving states that a unit test can't enumerate. A parser or an overlay needs a test.

## Docs

Most code changes need no doc change. Before you change an area, read its page in `docs/internals/`.

- `docs/internals/` records why: each architectural decision, the constraint that decided it, and traps the source can't show. Before you add a paragraph, ask what a maintainer would get wrong without it. Leave out how-to steps, code tours, file lists and test results.
- `docs/user/` helps players do a task, in the mod's voice, with no implementation details or contributor tooling. Update the section when how to use a feature changes.
- `docs/operations/` holds setup, run and release procedures. Its reader is an agent, and the machine, desktop and accounts belong to the user, so write "the user's".
- State each fact once, in the page that owns it, and link to the source instead of copying it. When a decision changes, rewrite or delete the text in the same change.
- `CONTRIBUTING.md` holds only the policy for outside contributors and links `docs/operations/development.md` for setup, running and checks. `docs/README.md` links every page with one line on what it covers.

## Rules

Each rule is paired with what enforces it. All of them run in `mise run check`, except the game tests and the CI rules.

| Rule | Enforced by |
| --- | --- |
| `core` imports nothing from Minecraft, Fabric or `edge` | `core-is-pure` in `mod/…/Rules.kt` |
| The release build sends for the player only in a feature DragSim Utils has | `no-automation` in `mod/…/Rules.kt` |
| The release build runs only on dragsim.co, unless the player turns DragSim Only off | `Game` gates every feature, `GameTest`, `DragSimTest` and the game test `OnlyOnDragSimTest` |
| A feature fires only in the area it names | `Feature` takes an `Area` and `Game` gates on it. `FeatureReplayTest` replays each fixture outside the area |
| A feature reads no area for itself, from the tab list or the sidebar, and sits in its area's folder | `area-by-declaration` in `mod/…/Rules.kt`, and `RulesTest` |
| The mod's text and colors come from its text layer | `text-by-role` in `mod/…/Rules.kt` |
| The mod's shapes are drawn by its kit, in the player's theme | `draw-with-kit` in `mod/…/Rules.kt` |
| Release code cannot use debug tools | The `main` and `debug` source sets |
| Raw captures stay out of git | They are written to `mod/run/`, which is git-ignored |
| A fixture holds no player name from a capture on this machine | `FixtureLeakTest` |
| Every feature shows what its transcript says | `FeatureReplayTest` replays each feature's fixtures |
| A feature that reads nothing from the server has a game test | `FeatureReplayTest` |
| Every feature has a section in the player docs | `RulesTest` |
| A feature's handler and readout average under 20µs | `FeatureReplayTest` |
| A capture type has one producer | `CaptureWriter` rejects a raw record with an event's type |
| The jars a player installs boot and open settings | `runProdClientGameTest` |
| A unit test run takes at most 30 seconds | `timeout` on `tasks.test` in `mod/build.gradle.kts`, and `go test -timeout 30s` in `check:backend` |
| A CI job takes at most 3 minutes | `.github/actions/time-limit`, the last step of every job in `ci.yml` |
| The mod stays inside its frame, tick and size caps | `PerfTest` against `mod/src/gametest/resources/perf-budgets.json`, in CI's `perf` job |
| A beta ships only when every check and game test passes | `.github/workflows/beta.yml` |
| Every capture type reaches the file | `CaptureTest`, and `BriefTest` for `step` |
| A redacted capture holds no player name the mod knows, and no typed chat | `RedactCaptureTest` |
| Specs hold their invariants | TLC on `specs/` |
| Go code is formatted and vetted | `gofmt -l` and `go vet` in `check:backend` |
| The website is type-safe | `tsc` with `strict` and the extra flags in `web/tsconfig.json` |
| Website code passes strict lint with no warnings | `typescript-eslint` strict-type-checked in `web/eslint.config.mjs` |
| Website failures are modeled with Effect, not `try` | `no-restricted-syntax` in `web/eslint.config.mjs` |
| Every interactive control comes from ui-sdk | `react/forbid-elements` and `no-restricted-imports` in `web/eslint.config.mjs` |
| Web dependencies are at least 14 days old | `minimumReleaseAge` in `web/pnpm-workspace.yaml` |
| `docs/README.md` links every page in `docs/` | `check:docs` |
| `docs/internals/` records why, not steps | `check:docs` rejects a recipe heading |
| `docs/operations/` addresses an agent, who owns no machine or account | `check:docs` rejects "your" |

When a mistake happens a second time, stop it at the highest level that works:

1. Change the structure so the mistake cannot be written, as the source sets do.
2. Use a type that rejects it.
3. Add a lint rule. In `mod/` that is `src/test/kotlin/io/github/noxaur/dsmod/rules/Rules.kt`: a pattern that must not appear in some files, a `fix` that says what to write instead, and an `example` line it must catch. In `web/` it is `eslint.config.mjs`, with a message that says what to write instead.
4. Add a test.
5. Write it in an `AGENTS.md`, only for a judgment call that no check can make.

Then add a row to the table. If a row has nothing enforcing it, enforce it in the same change. Delete a rule once the structure makes the mistake impossible.

A rule failure prints the file, the line and the fix. For an exception the user agreed to, end the line with a comment: `rule:<id> <reason>` in `mod/`, or `eslint-disable-next-line <rule> -- <reason>` in `web/`.

## Checks

- `mise install` gives you the pinned JDK, Go, Node and pnpm.
- `mise run check` must pass. It runs `check:mod`, `check:backend`, `check:web` and `check:docs`, and each one runs alone too.
- Test the real implementation instead of copying its logic into the test.
- CI runs the same checks, and `PerfTest` in a real game. When a job nears its 3 minutes, make it faster or move the slow part to the beta pipeline. Raise a limit or a perf cap only when the user agrees.

## Branches and pull requests

- Use a short branch name of at most three hyphenated words, such as `capture-writer` or `fix-tab-parser`.
- Make a PR only when asked. Before you create one, load the `pr` skill and follow it. It is mandatory.

## Code and residue

- Agents read code well, so add comments only where the code can't speak for itself.
- Keep plans, research notes and scratch files outside the worktree.
