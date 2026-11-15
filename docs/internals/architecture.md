# Architecture

ds-mod has three parts. The mod runs in the player's game. The backend is our own REST service, and the website reads from it too. `api/openapi.json` is the contract between them.

## The backend is an Effect API inside a Void app

Void deploys the backend to Cloudflare Workers and owns its bindings. The API itself is defined and handled with Effect, and one catch-all route hands each request to it. The split keeps the way out open. A move to a single machine needs a new entry point, and no change to a rule.

The contract is generated from the backend's API definition, because a hand-written one drifts. The mod is Kotlin and can share no types with the backend, so a file in a neutral format is the one thing both sides can hold to.

## The mod knows the game only from packets

dragsim.co is someone else's server, closed and custom, so it has no API for ds-mod. Everything the mod knows arrives in the packets that the server sends the client. That is why agents work from captures, which record what the server showed, instead of from a description of the game. The capture format is in [mod/AGENTS.md](../../mod/AGENTS.md#seeing-the-game).

## core is pure, edge touches Minecraft

The `edge` package holds the Fabric callbacks and is the only release code that touches Minecraft. The `core` package holds the logic and imports nothing from Minecraft or Fabric. A test of `core` then runs on a plain JVM, with no game to start. `core-is-pure` in `Rules.kt` keeps the import out.

A feature that only changes how the game draws, such as Fullbright, handles no event, so its code is a mixin in the edge that asks its setting. It still has an empty class in `core`, because the one list of features is what the settings, the docs check and the tests walk. A second list for these would be one more place to forget.

## A capture is a recording of what the features saw

The edge turns what the server sends into a `GameEvent`, and the capture records the events as they were posted. A test replays the recording through the same `Game` the player runs, so nothing stands between a capture and a test that could drift from the real input.

A type of data is either an event or a raw record, and never both. Raw records exist because nobody can design an event for data they have not seen: the recorder writes everything else the server sent, with full fidelity, so the first capture of a screen shows what its event must carry. A feature that needs that data promotes it to an event. Two producers for one type would let a test pass on data the player's game never builds.

Only an excerpt of a capture goes in git, as a feature's fixture, with the player names and their chat replaced. The transcript beside it is the expected result, written as a player would describe it, so a reviewer who knows the game can judge it without reading the code.

## Features run on events and ticks, never in a frame

A feature handles events, and `Game` rebuilds its HUD block and slot marks after an event the feature could see, and four times a second for its clocks. The frame only draws what the last rebuild left. A feature's code then can't cost a frame, however it is written, and its cost is measured in one place.

A feature returns what it shows instead of calling a UI layer. The replay test can then read it without Minecraft, and a feature that is switched off shows nothing without having to remember to hide. `Game` compares each rebuilt readout with the last one, and `Readouts` in the edge hands only a changed one to the layers.

Hiding a server line is the one thing a handler decides on the spot. Fabric asks whether a line may show before it reaches the chat, so `Game.post` returns the answer and the edge posts chat from that question. A line another mod refuses first never reaches the features, because Fabric stops asking at the first refusal.

A chat line or an alert is collected the same way and delivered at the end of the tick. Printed from inside the handler, it would land above the server line that caused it, because the handler runs before that line reaches the chat.

Placement belongs to `Readouts`, which keeps one position per feature id. A feature returns a block and knows nothing about the screen.

`Slots` drops its marks when a container screen closes, and marks handed over earlier may be for the container that was open before. So `Readouts` clears the marks when a container screen opens and hands them over again on the next tick, after the poll has seen the new container. It hands them over again when the screen closes too: closing the player's own inventory changes no container, so no event would bring the hotbar's marks back.

## Shared state is folded once

`Game` folds the tab list, the sidebar and the open container into `TabState`, `SidebarState` and `ContainerState` before any feature sees the event. Twenty features that each parsed the tab list for the area would pay twenty times and drift apart.

The area lock lives in `Game`, not in the features. Each feature used to compare the `Area` row itself, and four features had three different checks: one hid its HUD but kept saving loot from other areas, and one ran wherever the area was unknown. A feature now names an `Area`, and outside it gets only the tab list and a change of world. Before the server names an area the player is in none, because a wrong count is saved for good and a late readout costs a quarter of a second. A change of world empties the tab and sidebar state, and each poller posts again even when its rows are the same, so the old lobby's area never outlives the hop and a hop between two identical lobbies does not leave the features deaf.

The sidebar is an event with every line and its styles, not a parsed summary, so that a layer can one day draw the mod's own sidebar in place of the game's from the same state the features read. Some places exist only there: the Dungeon Hub's tab list has no info row, and the Void Sepulture is still `Area: The End` in the tab list. So an `Area` is told by a tab row, a sidebar place, or both.

A tab list is one event with every row, because it is small and features read it as a whole. A container is an open event followed by one event per changed slot, the way the server sends it. A whole inventory per change would make a capture of an hour's grinding too large to keep.

Reading an item out of Minecraft is the costly part of a menu: a chest of 54 items with long lore took 5.5 ms in one tick. So the poll reads for at most a millisecond a tick, and a big menu arrives over a few ticks. A slot that is still null just after the menu opened may only be unread.

## The mod runs only on dragsim.co

Every feature reads lines in dragsim.co's own words, so on another server it can only misread one, and a wrong count is saved for good. The gate lives in `Game`, which treats every feature as switched off while it is closed, so a feature can't forget it, and opening it replays the tab list and sidebar the same way switching one feature back on does. A feature in the edge has no `Game` between it and Minecraft, so it asks `DsModClient.active` itself.

The edge decides once per connection, because Fullbright asks the gate every frame. A player may join by the IP or by a name of their own, so besides the name they typed, the edge compares the machine the connection reached with dragsim.co's addresses. It looks those up at start and again after each join, off the client thread, because a lookup can stall for seconds and the join can't wait for it. A hardcoded IP would stop matching the day the server moves, and nothing would say so. A player who wants the mod elsewhere turns the setting off. The debug build lifts the gate instead, because `runClient` and the game tests run in singleplayer, and a test that had to turn a setting off first would be one more thing to forget. `Game` is open by default for the same reason, so a unit test or a replay never meets the gate.

## Packets that leave no state are posted as they arrive

The tab list and the open container are client state, so the edge polls them. An action bar or an entity's death leaves nothing to poll, and their order against each other carries meaning: the server sends the Combat XP in the action bar right after the death of the mob the player killed, and that pairing is the only sign of whose kill it was. So `ServerPacketsMixin` posts both from the packet handler, with the time each was handled. Polled once a tick, the two would arrive in the poll's order and not the server's.

A block the server placed before its chunk reached the client comes with no update at all, so a marker head that already stands is found by reading each chunk as it loads. Only a head with a skin is posted from there. The End is decorated with plain heads, and one of those posted as a marker would send the Protector's beam to a wall.

## Saved state belongs to the feature, and the disk to the edge

A feature declares one immutable state object and replaces it to change it. The edge loads it at start and writes the changed ones about once a minute, on its own thread, through a temporary file and a rename. A file that fails to parse is moved aside, because a tracker's history is worth more than a clean start.

MoulConfig has a data file class, and ds-mod does not use it: it writes on the caller's thread, and after a failed load its next save overwrites the bad file.

## Metrics are pulled

`Metrics` adds up timings, counts and failures in memory, and a flush hands one snapshot to each sink. Recording costs the client thread no I/O, and a new receiver is one more sink. Today the release build logs failures, and a running capture writes each snapshot into the file.

The `unparsed` counter matters most. The server is closed and changes without notice, so a feature that stops matching looks the same as a quiet game. A line that reached a feature and failed to parse is the only signal.

## The recorder is in the release jar, and runs only when started

Developers' accounts cannot reach all of the server's content, so players will record it for them. That needs the recorder in the jar a player installs, though it reads private client fields and records everything, chat included. Two things make that safe. It starts only from the debug build's `/dscapture` and `-Pbrief`, so while none runs every hook is one null check and the recorder costs a frame nothing. And the recorder only ever reads what the server already sent the client, so it cannot send for the player.

The rest of the developer tools stay in `src/debug`, a separate source set that `src/main` can't see, so the compiler keeps them out of the release jar. They are `/dscapture`, `/dskit`, `cutFixture` and the window title that names a `-Pbrief`. The debug jar holds the release jar inside it, so the release code runs the same in both.

The recorder reads private fields, which needs an access widener, and a mod declares only one. So the jar's file is ours followed by MoulConfig's lines, merged by the build.

Recording is client-thread work that no thread can do for it, because it reads game state. Writing happens on another thread, and the writer opens the file on its own thread, so starting a recording touches no disk on the client thread.

A recording reads every item and name in view four times a second, and Minecraft's codec was most of what that cost: over a millisecond a pass with a full inventory and a crowd of armored mobs. Almost none of them change between passes, so the session encodes a value only when it differs from the copy it kept, and `PerfTest` caps both passes.

## A capture is redacted by rules that say where a name sits

A capture holds player names and private chat, and a fixture cut from it goes into a public repository. `Redactor` rewrites a capture so it holds neither. `PlayerNames` is the one place that knows where the server puts a name, so the names `cutFixture` replaces everywhere are the names `FixtureLeakTest` looks for. The test cannot see typed text or a name kept to where a rule found it, which is why a fixture is still read before it is kept. Both live in `core` rather than the debug build, so that a release build can redact a capture as well.

A redactor fails in two directions. A name it misses leaves the machine, and a word it takes for a name changes what the server sent, so a fixture replays a line the server never wrote. Each rule therefore says where in a text a name sits, and a name is always replaced there. A name a rule found is also replaced wherever else it stands, because names turn up where no rule looks, such as a head's item name, unless that would rewrite the server's own words. Two kinds are kept to the places a rule found them. A plain name on a player entity may be an NPC's, since the server's NPCs are player entities, some with plain names such as `Dungeons`. And a player can be named what the server calls itself or a place, as a staff account is named after the server, so a name that the sidebar's title, its `⏣` place, the tab list's area row or its header also shows is not replaced across the capture. A player named after a game word the server writes only in other places, such as `Eye`, still has it replaced everywhere.

What a player typed is found by shape, not by a list of formats. The server's whisper, party and guild lines are in no capture, so a list would be a guess that passes its own tests. The shape is a known name followed by a `: ` on the same line, which the server never writes in its own lines; across every capture so far, no server line lost text to it. It costs the text of a server line that names a player and then has a `: `, and of a message that starts like a speaker's, `Word: `, without a tag such as `[NPC]`. And a message from a player no line of the capture names keeps both the name and the text.

Typed text is found only in a `system` event, so a raw `chat` record, whose event carries the same line, is dropped, and so is an older capture's `system` line that does not read as today's event. A component that spells a name across several pieces, one per color, is replaced by its redacted plain text, because no single string holds the name to replace, and an older raw text that names someone loses its `§` codes for the same reason.

## The release jar carries its own MoulConfig

Settings use MoulConfig, the settings screen SkyHanni uses. MoulConfig ships inside the release jar, relocated under `io.github.noxaur.dsmod.deps`, so ds-mod can't clash with another mod that carries a different MoulConfig. A MoulConfig type in a public signature therefore breaks any code compiled outside the jar, such as the game tests, which is why `DsModClient` exposes `DsModConfig` and not the `ManagedConfig` that holds it. `runProdClientGameTest` loads the built jars the way a player does, because a packaging mistake such as a missing access widener passes every unit test.

## Specs for interleavings

A state machine whose states interleave in ways a unit test can't enumerate gets a TLA+ spec in `specs/`. The mod's Gradle build runs TLC on every spec, so the spec and the code are checked in the same run. A parser or an overlay gets a test instead.

## A feature states a role, the text layer owns the color

Minecraft mods usually write color codes inline, and their colors drift from feature to feature. In ds-mod a feature says what a piece of text means (body, value, muted and so on) and `core.text` maps that role to a color. The palette then lives in one file, and `text-by-role` in `Rules.kt` fails the build on a color code or a raw color anywhere else in the release code.

Text the server sent is a separate type, `ServerText`, which carries whatever color the server chose. A feature reads one and builds the other, so the server's colors cannot leak into what the mod shows.

## UI layers keep what they are given

Each surface (chat, HUD, alerts, container slots, tooltips, world marks) has one object in `edge` that draws a plain model from `core`. A model is handed over when something changes, and the layer converts it to Minecraft's types once and redraws it from then on. A feature that built its text every frame would allocate in the render loop, where a dropped frame is a bug, so the layers offer no per-frame hook.

## Chrome is drawn in code, from a theme

Panels, buttons, slots and bars are a few filled rectangles each, drawn by `Kit` in the colors of a `Theme`. Minecraft's own screens stretch nine-slice textures instead, and ds-mod does not: a second look would need a second set of textures drawn by hand, and a new shape would need an image editor. In code a theme is one list of colors and a shape is a function, and `draw-with-kit` in `Rules.kt` keeps every other file from drawing its own. Only the icons are textures, white so that one sprite takes any color.

Two themes ship, because the references players know disagree: resource-pack screens are dark, and Minecraft's own are light gray. A light panel can't carry the text layer's colors, which were picked for chat and the HUD and wash out on gray. So a label on a panel is not role text. It is a plain string in the theme's ink, and role text sits only on a well or the HUD's plate, which stay dark in every theme. A theme that made those light would break every colored row.

## The HUD's scale snaps to screen pixels

The kit's edges are one pixel wide, and the HUD is the one place a player scales freely. At a scale of 1.3 a one-pixel edge covers 2.6 screen pixels at GUI scale 2, so some edges come out two pixels wide and others three, and the frame looks broken. `Readouts` therefore rounds a block's scale, times the HUD's, to a whole number of screen pixels per HUD pixel. The editor steps by one screen pixel for the same reason: a smaller step would change the saved number and draw nothing new.

A block's saved place is the corner of its plate, so a block stays where the player dropped it whether the plate is shown or not.

## World marks reach Minecraft once per tick

`World` draws through Minecraft's gizmos, the shapes its own debug views use. It submits them at the end of each client tick, and Minecraft replays the tick's gizmos on every frame until the next one. Submitting from Fabric's per-frame gizmo event drew nothing in the game tests, and would do the work every frame instead of twenty times a second.

## Free Look takes the mouse before the player does

The player's rotation is what the game aims with and what it sends to the server. Free Look must leave it alone, or every look around would turn the head other players see and move the crosshair's target. So `MouseTurnMixin` stops the turn before it reaches the player and `Cameras` spends it on an angle of its own, which `CameraMixin` gives only to the camera. Setting the rotation and putting it back afterwards would send the turn in between.

Camera's height and tilt are applied after the game has put the camera at the eyes and before it backs the camera out. The game's own wall check then runs from the lifted start along the tilted angle, so neither setting can push the view through a block.

The key's press comes from `KeyMapping.click`, the place the game counts presses for its own key binds, and its release is read from the key's state each frame. A press read from the state would miss a tap that starts and ends inside one frame, and Toggle mode is nothing but taps.

## Private fields are read through accessor mixins

Minecraft keeps an open container's position on screen in protected fields, and Fabric's screen events fire outside the transform that places the slots. `ContainerScreenAccessor` reads the two fields. `ChatComponentAccessor` does the same for the chat's messages and wrapped lines, which Copy Chat needs to tell what a press landed on.

## CI measures the mod in a real game

"Fast and smooth" is checked, not promised. `PerfTest` boots the jars a player installs and reads the mod's own `Metrics` timers, so the numbers are the ones a capture records in play. A unit benchmark would miss the draw cost, which exists only inside Minecraft's renderer.

Runners differ in speed, and one runner differs from run to run. `PerfTest` times a fixed loop on the machine it runs on and scales every time to the device named in `perf-budgets.json`, so a cap means the same on a laptop and on a runner. Each cap gates the median of twelve windows, because one window on a shared runner can be several times slower than the rest.

The game takes about 40 seconds to boot on a runner, where it renders in software. So CI runs `PerfTest` alone, and the other game tests run before a beta release. A broken screen can therefore reach `main`, and it is caught within two days, before any player gets it.

## A game test runs behind the developer

Agents run the game tests while a person works on the same machine, often with their own game open. So the test game opens without the keyboard, under the window class `dsmod-gametest`, and writes its sound to a file. All of it lives in the `gametest` source set, so `runClient` and the release jar are untouched.

The window class is what lets a window manager tell the test from the game the developer plays, which has the same title. On Hyprland the test installs its own named window rule through `hyprctl` and needs nothing in the developer's config. A rule in a dotfile would be one more setup step, and the core works with no setup.

Both settings must be made from inside the game, just before it creates its window. A GLFW window hint lasts only until the next window, and Minecraft resets the hints first, so a launch flag can't carry it. OpenAL reads `ALSOFT_CONF` from the process environment when the first device opens, which a Java system property can't set, and Loom's production run task takes no environment. `wave` is the only driver listed, so when it fails the game is silent instead of falling back to the speakers.

## CI's time limit is a step, not a timeout

`timeout-minutes` cancels a job and says only that it was cancelled. It also can't tell a slow change from a cold cache: the first run after a version bump downloads Minecraft and takes about four minutes whatever the change does. The `time-limit` action runs last, reads when the job started, and fails with the time and what to do about it. It lets a run through when the cache was cold.
