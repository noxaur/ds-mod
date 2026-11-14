# mod

The Fabric mod, written in Kotlin. Read the root `AGENTS.md` first.

## How it works

The server is someone else's, closed and custom, so everything ds-mod knows about the game arrives in packets. The `edge` package holds the Fabric callbacks and is the only release code that touches Minecraft. It turns what the server sends into a `GameEvent` and posts it to `Game`. The `core` package holds the features and imports nothing from Minecraft or Fabric, so it runs in plain JVM tests.

Settings use MoulConfig, the settings screen SkyHanni uses. A setting is an annotated field in `DsModConfig`, and `/dsmod` opens the screen.

Load the `ui` skill before you add or change anything the player sees or hears. It is at `mod/.claude/skills/ui/SKILL.md`.

## Features

A feature is one class in `core/feature/<area>/`, listed in `Features.kt`, with its switch in `DsModConfig` and a `### <Name>` section in `docs/user/features.md`. Copy the feature closest to yours:

| To | Copy |
| --- | --- |
| Follow lines in chat | `DragonTimer` |
| Read the tab list's info rows | `GolemStatus` |

- A feature reads `GameEvent`s and the shared state, `tab` and `container`. It returns its HUD block from `hud(now)`, built with `block { row("Label") { value(..) } }`, and its slot marks from `slots(now)`. `Readouts` in `edge` hands both to the UI layers when they change. Minecraft, the clock, the disk, placement and drawing belong to the shared layers, so extend the layer when a feature needs more from it.
- Chat, alerts, tooltips and world marks have layers, and no route from a feature yet. The first feature that needs one adds it to `Feature`, `Game` and `Readouts`, and to the transcript in `FeatureReplayTest`.
- State that outlives the session is one immutable `@Serializable` class given to `saved()`, with a default for every field. Replace its `value` to change it, because a change made in place is never written.
- Text the server sent is a `ServerText`, and text the mod shows is a `Text` from `core.text`. Match the server's text on `ServerText.plain`, anchored at the start of the line, because a player can type anything after their name.
- When a line reaches a feature's branch and then fails to parse, call `count("unparsed")`. That counter is how a format change on the server shows up.
- Clear the state that belongs to one world on `WorldChanged`.
- Every feature has a fixture, `src/test/resources/features/<id>/<name>.jsonl`, cut from a real capture. `FeatureReplayTest` replays it and writes the transcript `<name>.txt`: each event, and the HUD block and slot marks each time they changed. The run that writes a transcript fails once, so that you read it before you keep it. To accept a changed readout, delete the transcript and run the test again.
- A feature that only reads events and returns a HUD block or slot marks needs no game test, because the replay covers it and `SurfacesTest` covers the layers. Write a game test when you add to `edge`.
- Cut a fixture with `./gradlew cutFixture`, never by hand. The window is in seconds from the capture's first line, and `--types system` keeps only the chat lines. Keep the lines your feature must ignore, since they prove it ignores them. It redacts every player name from the whole capture, each typed chat message and each skin, and `FixtureLeakTest` compares every fixture with the captures in `run/`, so run the check before you delete the capture. Read the fixture before you keep it, because a name the server writes in a line no rule knows is not redacted. `PlayerNames` is the one place that knows where names sit, so teach it there.
- DragSim Utils is all rights reserved, and its source is published to be read. Read it to learn what the server sends and how a feature should behave, then write ds-mod's own code and prove it with a capture. Copy no code and no data file from it. When a feature needs a table the game can't show, tell the user.

## Release and debug

The code is split into two source sets, and each becomes its own mod.

- `src/main` is the release build, the jar players install. It cannot see debug code, so the compiler keeps developer tools out of it.
- `src/debug` holds developer tools such as capture. The debug jar contains the release jar plus these tools, and `runClient` loads both.
- Put a tool in `src/debug` when only a developer or an agent needs it.
- `runClient` also loads the third-party mods in the `devMods` configuration, such as Sodium and Mod Menu, and logs in with the user's Microsoft account through DevAuth. These mods never ship in a jar, and the game tests run without them.

## Seeing the game

Agents see the game through captures. A capture is a JSONL file of what the server showed the client, one event per line. With the debug build, the user types `/dscapture` in game to start one and again to save it, and it lands in `dsmod/captures/` inside the game folder.

Each line has a time `t`, a `type` and `data`. A type is either an event or a raw record.

Events are the `GameEvent`s that features see, recorded as they were posted, and `readCapture` gives them back in a test:

- `system` for a line in chat. Player chat arrives this way too.
- `world` for entering a world.
- `tablist` for the rows of the tab list in the order the client draws them, when one changes.
- `container` for a menu the player opened, or their own inventory again, and `slot` for each slot that changed. A slot carries the item's id, count, name, lore and `custom_data`.

Raw records are for you to read. No feature sees one until it is promoted:

- `chat`, `actionbar` and `title`.
- `sidebar`, `bossbars` and `tabheader`, the tab list's header and footer.
- `itemcomponents` for the components a `slot` leaves out, such as `tooltip_style`, `profile` and `item_model`, in Minecraft's own codec JSON.
- `entities` for players, named entities and anything wearing or holding an item, each with the block it stands in. Glowing entities, silverfish and creepers, displays (`text`, `item`), item frames (`item`, `rotation`, `facing`) and dropped named items come too.
- `status` for the player's block, health and XP.
- `sound`, `particles` and `block` for what the server played, spawned or changed.
- `swing` and `use` for the player's attack and use buttons, before the game acts: position, yaw, pitch, the held item's `item`, `name` and `custom_data` `itemId`, and the `target` entity or `block` under the crosshair.
- `death` for the server's death event on an entity, and `unload` for a living entity the client dropped, with its `reason`. A mob that only left has `unload` and no `death`.
- `skulls` for skull blocks within four chunks, with `state` (rotation) and `profile`, and `blockstates` for the blocks in `BlockScan.kt`'s watch-list there. Both are entries like `entities`, keyed `x,y,z`, and include what stood there before the capture began.
- `map` for a map the player carries or an item frame holds, when it changes: `colors` is base64 of the zlib-deflated 128x128 color bytes, present only when the pixels changed, and `decorations` are the markers.
- `hud` for the rows of each HUD block ds-mod showed, and `metrics` for how long each feature took and what it counted.

An event's text is `plain` plus `spans`, with `§` codes already resolved into the spans. A raw record's text is plain `text` plus the full `component`. `itemcomponents` and `entities` record all entries once with `"full": true`, then only the entries that changed, with null for a removed one.

- Work from captures. When none covers the case, name the screen or action you need and ask the user to record it.
- The live server has real players on it, so only the user connects there.
- Captures hold player names and private chat, so only a fixture goes in git.
- To promote a raw type, design the `GameEvent` from a capture that shows the data, post it from `edge`, and delete its recorder in `CaptureSession`. A type has one producer, and `CaptureWriter` rejects a raw record that uses an event's type.
- When you add a capture type, add it to `CaptureTest`, which makes a real server send each type and checks the file.
- To see a screen, write a client game test in `src/gametest` that opens it and calls `takeScreenshot`. The image lands in `build/run/clientGameTest/screenshots/`.


## Checks

- Run everything here through `./gradlew`, which fetches the rest.
- `./gradlew check` must pass. It runs the tests and TLC on every spec in `../specs/`.
- When you change `edge`, a screen or the build, also run `./gradlew runClientGameTest runProdClientGameTest`. Each opens a game window for a few seconds. The second one loads the built jars the way a player does, so it catches packaging mistakes.
- Versions are pinned in `gradle.properties`. The server decides the Minecraft version, so bump it only when the user asks.
- A library that ships inside the release jar goes in the `shade` configuration and gets a `relocate` line and a `NOTICE` entry.
