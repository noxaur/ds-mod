# Architecture

ds-mod has three parts. The mod runs in the player's game. The backend is our own REST service, and the website reads from it too. `api/openapi.yaml` is the contract between them, so a change to an endpoint starts there and reaches the backend before any caller.

## The mod knows the game only from packets

dragsim.co is someone else's server, closed and custom, so it has no API for ds-mod. Everything the mod knows arrives in the packets that the server sends the client. That is why agents work from captures, which record what the server showed, instead of from a description of the game. The capture format is in [mod/AGENTS.md](../../mod/AGENTS.md#seeing-the-game).

## core is pure, edge touches Minecraft

The `edge` package holds the Fabric callbacks and is the only release code that touches Minecraft. The `core` package holds the logic and imports nothing from Minecraft or Fabric. A test of `core` then runs on a plain JVM, with no game to start. `core-is-pure` in `Rules.kt` keeps the import out.

A feature that only changes how the game draws, such as Fullbright, handles no event, so its code is a mixin in the edge that asks its setting. It still has an empty class in `core`, because the one list of features is what the settings, the docs check and the tests walk. A second list for these would be one more place to forget.

## A capture is a recording of what the features saw

The edge turns what the server sends into a `GameEvent`, and the capture records the events as they were posted. A test replays the recording through the same `Game` the player runs, so nothing stands between a capture and a test that could drift from the real input.

A type of data is either an event or a raw record, and never both. Raw records exist because nobody can design an event for data they have not seen: the debug build writes everything else the server sent, with full fidelity, so the first capture of a screen shows what its event must carry. A feature that needs that data promotes it to an event. Two producers for one type would let a test pass on data the player's game never builds.

Only an excerpt of a capture goes in git, as a feature's fixture, with the player names and their chat replaced. The transcript beside it is the expected result, written as a player would describe it, so a reviewer who knows the game can judge it without reading the code.

## Features run on events and ticks, never in a frame

A feature handles events, and `Game` rebuilds its HUD block and slot marks after an event the feature could see, and four times a second for its clocks. The frame only draws what the last rebuild left. A feature's code then can't cost a frame, however it is written, and its cost is measured in one place.

A feature returns what it shows instead of calling a UI layer. The replay test can then read it without Minecraft, and a feature that is switched off shows nothing without having to remember to hide. `Game` compares each rebuilt readout with the last one, and `Readouts` in the edge hands only a changed one to the layers.

Hiding a server line is the one thing a handler decides on the spot. Fabric asks whether a line may show before it reaches the chat, so `Game.post` returns the answer and the edge posts chat from that question. A line another mod refuses first never reaches the features, because Fabric stops asking at the first refusal.

A chat line or an alert is collected the same way and delivered at the end of the tick. Printed from inside the handler, it would land above the server line that caused it, because the handler runs before that line reaches the chat.

Placement belongs to `Readouts`, which keeps one position per feature id. A feature returns a block and knows nothing about the screen.

`Slots` drops its marks when a container screen closes, and marks handed over earlier may be for the container that was open before. So `Readouts` clears the marks when a container screen opens and hands them over again on the next tick, after the poll has seen the new container.

## Shared state is folded once

`Game` folds the tab list and the open container into `TabState` and `ContainerState` before any feature sees the event. Twenty features that each parsed the tab list for the area would pay twenty times and drift apart.

A tab list is one event with every row, because it is small and features read it as a whole. A container is an open event followed by one event per changed slot, the way the server sends it. A whole inventory per change would make a capture of an hour's grinding too large to keep.

Reading an item out of Minecraft is the costly part of a menu: a chest of 54 items with long lore took 5.5 ms in one tick. So the poll reads for at most a millisecond a tick, and a big menu arrives over a few ticks. A slot that is still null just after the menu opened may only be unread.

## Saved state belongs to the feature, and the disk to the edge

A feature declares one immutable state object and replaces it to change it. The edge loads it at start and writes the changed ones about once a minute, on its own thread, through a temporary file and a rename. A file that fails to parse is moved aside, because a tracker's history is worth more than a clean start.

MoulConfig has a data file class, and ds-mod does not use it: it writes on the caller's thread, and after a failed load its next save overwrites the bad file.

## Metrics are pulled

`Metrics` adds up timings, counts and failures in memory, and a flush hands one snapshot to each sink. Recording costs the client thread no I/O, and a new receiver, such as the backend, is one more sink. Today the release build logs failures, and the debug build writes each snapshot into the capture.

The `unparsed` counter matters most. The server is closed and changes without notice, so a feature that stops matching looks the same as a quiet game. A line that reached a feature and failed to parse is the only signal.

## Debug tools are a separate source set

The release build must send nothing for the player, because the server bans automated actions. Developer tools such as capture read private fields and record everything, so they must never reach a player. `src/debug` is a separate source set that `src/main` can't see, so the compiler keeps the tools out of the release jar. The debug jar holds the release jar inside it, so the release code runs the same in both.

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

## Slot marks need one accessor mixin

Minecraft keeps an open container's position on screen in protected fields, and Fabric's screen events fire outside the transform that places the slots. `ContainerScreenAccessor` reads the two fields. The release jar declares no access widener of its own, so this mixin is the one place release code reaches into Minecraft's internals.

## CI measures the mod in a real game

"Fast and smooth" is checked, not promised. `PerfTest` boots the jars a player installs and reads the mod's own `Metrics` timers, so the numbers are the ones a capture records in play. A unit benchmark would miss the draw cost, which exists only inside Minecraft's renderer.

Runners differ in speed, and one runner differs from run to run. `PerfTest` times a fixed loop on the machine it runs on and scales every time to the device named in `perf-budgets.json`, so a cap means the same on a laptop and on a runner. Each cap gates the median of twelve windows, because one window on a shared runner can be several times slower than the rest.

The game takes about 40 seconds to boot on a runner, where it renders in software. So CI runs `PerfTest` alone, and the other game tests run before a beta release. A broken screen can therefore reach `main`, and it is caught within two days, before any player gets it.

## CI's time limit is a step, not a timeout

`timeout-minutes` cancels a job and says only that it was cancelled. It also can't tell a slow change from a cold cache: the first run after a version bump downloads Minecraft and takes about four minutes whatever the change does. The `time-limit` action runs last, reads when the job started, and fails with the time and what to do about it. It lets a run through when the cache was cold.
