# Architecture

ds-mod has three parts. The mod runs in the player's game. The backend is our own REST service, and the website reads from it too. `api/openapi.yaml` is the contract between them, so a change to an endpoint starts there and reaches the backend before any caller.

## The mod knows the game only from packets

dragsim.co is someone else's server, closed and custom, so it has no API for ds-mod. Everything the mod knows arrives in the packets that the server sends the client. That is why agents work from captures, which record what the server showed, instead of from a description of the game. The capture format is in [mod/AGENTS.md](../../mod/AGENTS.md#seeing-the-game).

## core is pure, edge touches Minecraft

The `edge` package holds the Fabric callbacks and is the only release code that touches Minecraft. The `core` package holds the logic and imports nothing from Minecraft or Fabric. A test of `core` then runs on a plain JVM, with no game to start. `core-is-pure` in `Rules.kt` keeps the import out.

## Debug tools are a separate source set

The release build must send nothing for the player, because the server bans automated actions. Developer tools such as capture read private fields and record everything, so they must never reach a player. `src/debug` is a separate source set that `src/main` can't see, so the compiler keeps the tools out of the release jar. The debug jar holds the release jar inside it, so the release code runs the same in both.

## The release jar carries its own MoulConfig

Settings use MoulConfig, the settings screen SkyHanni uses. MoulConfig ships inside the release jar, relocated under `io.github.noxaur.dsmod.deps`, so ds-mod can't clash with another mod that carries a different MoulConfig. `runProdClientGameTest` loads the built jars the way a player does, because a packaging mistake such as a missing access widener passes every unit test.

## Specs for interleavings

A state machine whose states interleave in ways a unit test can't enumerate gets a TLA+ spec in `specs/`. The mod's Gradle build runs TLC on every spec, so the spec and the code are checked in the same run. A parser or an overlay gets a test instead.

## A feature states a role, the text layer owns the color

Minecraft mods usually write color codes inline, and their colors drift from feature to feature. In ds-mod a feature says what a piece of text means (body, value, muted and so on) and `core.text` maps that role to a color. The palette then lives in one file, and `text-by-role` in `Rules.kt` fails the build on a color code or a raw color anywhere else in the release code.

## UI layers keep what they are given

Each surface (chat, HUD, alerts, container slots, tooltips, world marks) has one object in `edge` that draws a plain model from `core`. A feature hands the model over when something changes, and the layer converts it to Minecraft's types once and redraws it from then on. A feature that built its text every frame would allocate in the render loop, where a dropped frame is a bug, so the layers offer no per-frame hook.

## World marks reach Minecraft once per tick

`World` draws through Minecraft's gizmos, the shapes its own debug views use. It submits them at the end of each client tick, and Minecraft replays the tick's gizmos on every frame until the next one. Submitting from Fabric's per-frame gizmo event drew nothing in the game tests, and would do the work every frame instead of twenty times a second.

## Slot marks need one accessor mixin

Minecraft keeps an open container's position on screen in protected fields, and Fabric's screen events fire outside the transform that places the slots. `ContainerScreenAccessor` reads the two fields. The release jar declares no access widener of its own, so this mixin is the one place release code reaches into Minecraft's internals.
