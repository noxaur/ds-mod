# ds-mod

ds-mod is a client-side quality-of-life mod for the Dragon Simulator server (dragsim.co), in the spirit of SkyHanni. It is a Fabric mod written in Kotlin.

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
- Follow the server's rules: no macros, scripts or other automated actions. Auto-clicking is allowed under 10 CPS, and only while the player is actively moving.
- A rule in prose gets skipped, so turn a repeated mistake into a check. See Rules.

## How it works

The server is someone else's, closed and custom, so everything ds-mod knows about the game arrives in packets. The `edge` package holds the Fabric callbacks and is the only release code that touches Minecraft. The `core` package holds the logic and imports nothing from Minecraft or Fabric, so it runs in plain JVM tests.

Settings use MoulConfig, the settings screen SkyHanni uses. A setting is an annotated field in `DsModConfig`, and `/dsmod` opens the screen.

Load the `ui` skill before you add or change anything the player sees or hears. It is at `.claude/skills/ui/SKILL.md`.

## Release and debug

The code is split into two source sets, and each becomes its own mod.

- `src/main` is the release build, the jar players install. It cannot see debug code, so the compiler keeps developer tools out of it.
- `src/debug` holds developer tools such as capture. The debug jar contains the release jar plus these tools, and `runClient` loads both.
- Put a tool in `src/debug` when only a developer or an agent needs it.
- `runClient` also loads the third-party mods in the `devMods` configuration, such as Sodium and Mod Menu, and logs in with the user's Microsoft account through DevAuth. These mods never ship in a jar, and the game tests run without them.

## Seeing the game

Agents see the game through captures. A capture is a JSONL file of what the server showed the client, one event per line. With the debug build, the user types `/dscapture` in game to start one and again to save it, and it lands in `dsmod/captures/` inside the game folder.

Each line has a time `t`, a `type` and `data`. The types are:

- `system` and `chat` for messages, `actionbar` and `title`.
- `sidebar`, `bossbars` and `tablist`.
- `container` for the open inventory, with every item and its components.
- `entities` for players, named entities and anything wearing or holding an item.
- `status` for health and XP.
- `sound`, `particles` and `block` for what the server played, spawned or changed.

Every text is stored as plain `text` plus the full `component`. `tablist`, `container` and `entities` record all entries once with `"full": true`, then only the entries that changed, with null for a removed one.

- Work from captures. When none covers the case, name the screen or action you need and ask the user to record it.
- The live server has real players on it, so only the user connects there.
- Captures hold player names and private chat, so keep them out of git.
- When you add a capture type, add it to `CaptureTest`, which makes a real server send each type and checks the file.
- To see a screen, write a client game test in `src/gametest` that opens it and calls `takeScreenshot`. The image lands in `build/run/clientGameTest/screenshots/`.

## Formal specs

- TLA+ specs live in `specs/`. A spec is `Name.tla` with `Name.cfg` beside it.
- When you change a state machine that has a spec, change the spec first and run TLC on it, then change the code.
- Write a spec for a new feature only when it has interleaving states that a unit test can't enumerate. A parser or an overlay needs a test.

## Rules

Each rule is paired with what enforces it. All of them run in `./gradlew check`, except the game tests.

| Rule | Enforced by |
| --- | --- |
| `core` imports nothing from Minecraft or Fabric | `core-is-pure` in `Rules.kt` |
| The release build sends nothing for the player | `no-automation` in `Rules.kt` |
| Release text and colors come from the text layer | `text-by-role` in `Rules.kt` |
| Release code cannot use debug tools | The `main` and `debug` source sets |
| Captures stay out of git | They are written to `run/`, which is git-ignored |
| The jars a player installs boot and open settings | `runProdClientGameTest` |
| Every capture type reaches the file | `CaptureTest` |
| Specs hold their invariants | TLC on `specs/` |

When a mistake happens a second time, stop it at the highest level that works:

1. Change the structure so the mistake cannot be written, as the source sets do.
2. Use a type that rejects it.
3. Add a rule to `src/test/kotlin/io/github/noxaur/dsmod/rules/Rules.kt`. A rule is a pattern that must not appear in some files, a `fix` that says what to write instead, and an `example` line it must catch.
4. Add a test.
5. Write it in this file, only for a judgment call that no check can make.

Then add a row to the table. If a row has nothing enforcing it, enforce it in the same change. Delete a rule once the structure makes the mistake impossible.

A rule failure prints the file, the line and the fix. For an exception the user agreed to, end the line with a comment `rule:<id> <reason>`.

## Checks

- `mise install` gives you the pinned JDK. Run everything else through `./gradlew`, which fetches the rest.
- `./gradlew check` must pass. It runs the tests and TLC on every spec.
- When you change `edge`, a screen or the build, also run `./gradlew runClientGameTest runProdClientGameTest`. Each opens a game window for a few seconds. The second one loads the built jars the way a player does, so it catches packaging mistakes.
- Test the real implementation instead of copying its logic into the test.
- Versions are pinned in `gradle.properties`. The server decides the Minecraft version, so bump it only when the user asks.
- A library that ships inside the release jar goes in the `shade` configuration and gets a `relocate` line and a `NOTICE` entry.

## Branches and pull requests

- Use a short branch name of at most three hyphenated words, such as `capture-writer` or `fix-tab-parser`.
- Make a PR only when asked. Before you create one, load the `pr` skill and follow it. It is mandatory.

## Code and residue

- Agents read code well, so add comments only where the code can't speak for itself.
- Keep plans, research notes and scratch files outside the worktree.
