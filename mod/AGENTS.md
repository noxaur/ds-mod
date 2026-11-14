# mod

The Fabric mod, written in Kotlin. Read the root `AGENTS.md` first.

## How it works

The server is someone else's, closed and custom, so everything ds-mod knows about the game arrives in packets. The `edge` package holds the Fabric callbacks and is the only release code that touches Minecraft. The `core` package holds the logic and imports nothing from Minecraft or Fabric, so it runs in plain JVM tests.

Settings use MoulConfig, the settings screen SkyHanni uses. A setting is an annotated field in `DsModConfig`, and `/dsmod` opens the screen.

Load the `ui` skill before you add or change anything the player sees or hears. It is at `mod/.claude/skills/ui/SKILL.md`.

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


## Checks

- Run everything here through `./gradlew`, which fetches the rest.
- `./gradlew check` must pass. It runs the tests and TLC on every spec in `../specs/`.
- When you change `edge`, a screen or the build, also run `./gradlew runClientGameTest runProdClientGameTest`. Each opens a game window for a few seconds. The second one loads the built jars the way a player does, so it catches packaging mistakes.
- Versions are pinned in `gradle.properties`. The server decides the Minecraft version, so bump it only when the user asks.
- A library that ships inside the release jar goes in the `shade` configuration and gets a `relocate` line and a `NOTICE` entry.
