# mod

The Fabric mod, written in Kotlin. Read the root `AGENTS.md` first.

## How it works

The server is someone else's, closed and custom, so everything ds-mod knows about the game arrives in packets. The `edge` package holds the Fabric callbacks and is the only release code that touches Minecraft. It turns what the server sends into a `GameEvent` and posts it to `Game`. The `core` package holds the features and imports nothing from Minecraft or Fabric, so it runs in plain JVM tests.

Settings use MoulConfig, the settings screen SkyHanni uses. A setting is an annotated field in `DsModConfig`, and `/dsmod` opens the screen.

Load the `ui` skill before you add or change anything the player sees, hears or operates. It is at `mod/.claude/skills/ui/SKILL.md`.

## Features

A feature is one class in `core/feature/<area>/`, listed in `Features.kt`, with its switch in `DsModConfig` and a `### <Name>` section in `docs/user/features.md`. Copy the feature closest to yours:

| To | Copy |
| --- | --- |
| Follow lines in chat | `DragonTimer` |
| Read the tab list's info rows, or the sidebar's the same way | `GolemStatus` |
| Read items and mark slots | `RarityOutlines` |
| Change how the game draws, reading nothing from the server | `Fullbright` |
| Count something and keep the total | `SlayerTracker` |
| Hide a line the server sends, and tell the player once that you did | `AbilitySpam` |
| Tell the player something once, and read a setting besides the switch | `LootAlerts` |
| Track a rate, and follow the player into and out of its area | `ZealotTracker` |
| Mark a place in the world | `BossBeacon` |
| Handle the player's own input on a vanilla screen, reading nothing from the server | `CopyChat`, with `CopyChats` in `edge` |

- A feature reads `GameEvent`s and the shared state: `tab`, `sidebar` and `container`. `sidebar` has every line as the server styled it, its `Key: value` lines through `info`, and the place. It returns its HUD block from `hud(now)`, built with `block { row("Label") { value(..) } }`, and its slot marks from `slots(now)`. `Readouts` in `edge` hands both to the UI layers when they change. Minecraft, the clock, the disk, placement and drawing belong to the shared layers, so extend the layer when a feature needs more from it.
- A feature passes its `Area` to `Feature(..)`. Outside it, `Game` gives the feature no event but the tab list, the sidebar and a change of world, and shows no readout, so a feature never asks where the player is. A feature locked to an area lives in that area's folder. For a new place, add an `Area` entry with the tab list row or the sidebar place a capture shows. A dungeon, the Dungeon Hub and a part of an area, such as the Void Sepulture, are told by the sidebar's `⏣` line. Prefer whichever of the two a capture shows arriving first. A feature in `edge` that sends or blocks input must ask the same lock, and none does yet.
- To say something once, a handler calls `notice(detail)` for a chat line or `alert(title, detail)` for a title with a sound. `Readouts` delivers both at the end of the tick.
- To keep a server line out of chat, its handler calls `hide()`. Every feature still sees the line. A feature that hides by default tells the player once and names its setting, as `AbilitySpam` does.
- A feature that reads nothing from the server, such as a camera or lighting change or a click on a vanilla screen, is a mixin in `edge/mixin` or a Fabric callback in `edge` that asks its setting, and an empty class in `core` that lists it. It has no fixture, so its game test `<Class>Test` proves the change with a check and a screenshot, as `FullbrightTest` does.
- A feature draws in the world by returning marks from `marks(now)`, as `BossBeacon` does. Tooltips have a layer and no route from a feature yet. The first feature that needs one adds it to `Feature`, `Game` and `Readouts`, and to the transcript in `FeatureReplayTest`.
- A drop line such as `RARE DROP! (Summoning Eye)` is parsed by `Drop.parse`. When two features read the same server line, they share its parser in `core/game`.
- State that outlives the session is one immutable `@Serializable` class given to `saved()`, with a default for every field. Replace its `value` to change it, because a change made in place is never written.
- Text the server sent is a `ServerText`, and text the mod shows is a `Text` from `core.text`. Match the server's text on `ServerText.plain`, anchored at the start of the line, because a player can type anything after their name.
- When a line reaches a feature's branch and then fails to parse, call `count("unparsed")`. That counter is how a format change on the server shows up.
- Clear the state that belongs to one world on `WorldChanged`. `Game` runs that handler too when the player switches the feature off, because a feature that is off sees no events, and runs its `TabList` and `Sidebar` handlers with the current ones when it is switched back on.
- Every feature has a fixture, `src/test/resources/features/<id>/<name>.jsonl`, cut from a real capture. `FeatureReplayTest` replays it and writes the transcript `<name>.txt`: each event, whether the feature hid it, what the feature said, and the HUD block and slot marks each time they changed. The run that writes a transcript fails once, so that you read it before you keep it. To accept a changed readout, delete the transcript and run the test again.
- A feature that only reads events and returns a HUD block or slot marks needs no game test, because the replay covers it and `SurfacesTest` covers the layers. Write a game test when you add to `edge`.
- Cut a fixture with `./gradlew cutFixture`, never by hand. The window is in seconds from the capture's first line, and `--types system` keeps only the chat lines. Keep the lines your feature must ignore, since they prove it ignores them. Keep the tab list for a feature locked to an area, and the sidebar with `--types ..,sidebar` when the sidebar tells its area, because a fixture without them replays as if the player never left it. It redacts every player name from the whole capture, each typed chat message and each skin, and `FixtureLeakTest` compares every fixture with the captures in `run/`, so run the check before you delete the capture. Read the fixture before you keep it, because a name the server writes in a line no rule knows is not redacted. `PlayerNames` is the one place that knows where names sit, so teach it there.
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
- `sidebar` for the sidebar's title and its lines from the top, when one changes.
- `actionbar` for the line above the hotbar, each time the server sends it.
- `death` for the server's death event on an entity that is not a player, with its type, name and position.
- `head` for a player head block the server placed, with its position.
- `container` for a menu the player opened, or their own inventory again, and `slot` for each slot that changed. A slot carries the item's id, count, name, lore and `custom_data`. A big menu's slots arrive over a few ticks.

Raw records are for you to read. No feature sees one until it is promoted:

- `chat` and `title`.
- `bossbars` and `tabheader`, the tab list's header and footer.
- `itemcomponents` for the components a `slot` leaves out, such as `tooltip_style`, `profile` and `item_model`, in Minecraft's own codec JSON.
- `entities` for players, named entities and anything wearing or holding an item, each with the block it stands in. Glowing entities, silverfish and creepers, displays (`text`, `item`), item frames (`item`, `rotation`, `facing`) and dropped named items come too.
- `status` for the player's block, health and XP.
- `sound`, `particles` and `block` for what the server played, spawned or changed.
- `swing` and `use` for the player's attack and use buttons, before the game acts: position, yaw, pitch, the held item's `item`, `name` and `custom_data` `itemId`, and the `target` entity or `block` under the crosshair.
- `unload` for a living entity the client dropped, with its `reason`. A mob that only left has `unload` and no `death` event.
- `skulls` for skull blocks within four chunks, with `state` (rotation) and `profile`, and `blockstates` for the blocks in `BlockScan.kt`'s watch-list there. Both are entries like `entities`, keyed `x,y,z`, and include what stood there before the capture began.
- `map` for a map the player carries or an item frame holds, when it changes: `colors` is base64 of the zlib-deflated 128x128 color bytes, present only when the pixels changed, and `decorations` are the markers.
- `hud` for the rows of each HUD block ds-mod showed, and `metrics` for how long each feature took and what it counted.
- `step` for the brief's step that starts on that line, with its `index` and `do`, and `done` after the last one. Cut a fixture's window from these.

An event's text is `plain` plus `spans`, with `§` codes already resolved into the spans. A raw record's text is plain `text` plus the full `component`. `itemcomponents` and `entities` record all entries once with `"full": true`, then only the entries that changed, with null for a removed one.

- Work from captures. When none covers the case, write a brief: a name for the window, and the ordered steps the user plays, each with a note or a timer where it needs one. [Record a capture](../docs/operations/development.md#record-a-capture) has the format.
- Finish the work that needs no capture, then end the response with one line the user answers with yes or no, such as "Record Golem tiers now? It takes about 3 minutes." On yes, start the client with the brief yourself and read the capture when the run ends.
- The live server has real players on it, so the user does all the playing there. You start the client and they join.
- Captures hold player names and private chat, so only a fixture goes in git.
- `actionbar`, `death` and `sidebar` were raw records until 2026-11-14, with another shape, so an older capture cannot be cut for them.
- To promote a raw type, design the `GameEvent` from a capture that shows the data, post it from `edge`, and delete its recorder in `CaptureSession`. A type has one producer, and `CaptureWriter` rejects a raw record that uses an event's type.
- When you add a capture type, add it to `CaptureTest`, which makes a real server send each type and checks the file.
- To see a screen, write a client game test in `src/gametest` that opens it and calls `takeScreenshot`. The image lands in `build/run/clientGameTest/screenshots/`.


## Checks

- Run everything here through `./gradlew`, which fetches the rest.
- `./gradlew check` must pass. It runs the tests and TLC on every spec in `../specs/`.
- When you change `edge`, a screen or the build, also run `./gradlew runClientGameTest runProdClientGameTest`. Each boots the game behind the user, without taking their keyboard or their speakers. The second one loads the built jars the way a player does, so it catches packaging mistakes.
- To hear what a game test played, read `audio.wav` in its run folder, `build/run/clientGameTest/` or `build/run/prodClientGameTest/`. It holds the whole run from the game's start.
- Versions are pinned in `gradle.properties`. The server decides the Minecraft version, so bump it only when the user asks.
- A library that ships inside the release jar goes in the `shade` configuration and gets a `relocate` line and a `NOTICE` entry.
