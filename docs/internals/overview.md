# Architecture overview

ds-mod has three parts. The mod runs in the player's game. The backend is our own REST service, and the website reads from it too. [`api/openapi.json`](../../api/openapi.json) is the contract between them. The words used here are defined in the [glossary](glossary.md).

## The mod knows the game only from packets

dragsim.co is someone else's server, closed and custom, and it offers ds-mod no API for what happens in the game. Everything the mod knows arrives in the packets the server sends the client. That is why agents work from captures, which record what the server showed, and not from a description of the game. See [captures](captures.md).

The server does have a public player API with no key. Only the backend calls it, for the reason in [profiles](profiles.md).

## core is pure, edge touches Minecraft

The `edge` package holds the Fabric callbacks and is the only release code that touches Minecraft. The `core` package holds the logic and imports nothing from Minecraft or Fabric. A test of `core` then runs on a plain JVM, with no game to start. `core-is-pure` in [`Rules.kt`](../../mod/src/test/kotlin/io/github/noxaur/dsmod/rules/Rules.kt) keeps the import out.

A feature that only changes how the game draws, such as Fullbright, handles no event, so its code is a mixin in the edge that asks its setting. It still has an empty class in `core`, because the one list of features is what the settings, the docs check and the tests walk. A second list for these would be one more place to forget.

## The mod runs only on dragsim.co

Every feature reads lines in dragsim.co's own words, so on another server it can only misread one, and a wrong count is saved for good. The gate lives in `Game`, which treats every feature as switched off while it is closed, so a feature can't forget it, and opening it replays the tab list and sidebar the same way switching one feature back on does. A feature in the edge has no `Game` between it and Minecraft, so it asks `DsModClient.active` itself, and nothing checks that it does.

The edge decides once per connection, because Fullbright asks the gate every frame. A player may join by the IP or by a name of their own, so besides the name they typed, the edge compares the machine the connection reached with dragsim.co's addresses. It looks those up at start and again after each join, off the client thread, because a lookup can stall for seconds and the join can't wait for it. A hardcoded IP would stop matching the day the server moves, and nothing would say so. A player who wants the mod elsewhere turns the setting off.

The debug build lifts the gate instead, because `runClient` and the game tests run in singleplayer, and a test that had to turn a setting off first would be one more thing to forget. `Game` is open by default for the same reason, so a unit test or a replay never meets the gate.

## Release and debug are separate source sets

`src/main` is the jar players install, and `src/debug` holds the developer tools: `/dscapture`, `/dskit`, `cutFixture`, the window title that names a `-Pbrief` and the title screen's Join DragSim button. `src/main` can't see `src/debug`, so the compiler keeps the tools out of the release jar, which a review could miss. The debug jar holds the release jar inside it, so the release code runs the same in both. The recorder is the exception that ships, for the reason in [captures](captures.md#the-recorder-is-in-the-release-jar-and-runs-only-for-a-contribution).

## The release jar carries no library

The release jar holds only ds-mod's own code. Gson comes with Minecraft and kotlinx.serialization with Fabric Language Kotlin, which every player installs beside it. A library carried inside the jar would have to be relocated under `io.github.noxaur.dsmod.deps`, so ds-mod can't clash with another mod that carries a different version of it, and then none of its types could appear in a public signature, because code compiled outside the jar, such as the game tests, would not link. The settings screen was MoulConfig until [ds-mod's own replaced it](settings.md#the-tree-is-the-only-schema-and-moulconfig-went).

`runProdClientGameTest` loads the built jars the way a player does, with Mod Menu beside them, because a packaging mistake such as a missing access widener or an entrypoint that does not link passes every unit test.

## The backend and the website keep each other's boundaries

The backend is an Effect API inside a Void app on Cloudflare, and the website is a Next.js app on Vercel that calls the backend from its own servers. Nothing in a browser talks to the backend. See [backend](backend.md) and [sign-in](sign-in.md).

The contract is generated from the backend's API definition, because a hand-written one drifts. The mod is Kotlin and can share no types with the backend, so a file in a neutral format is the one thing both sides can hold to.

## Specs for interleavings

A state machine whose states interleave in ways a unit test can't enumerate gets a TLA+ spec in [`specs/`](../../specs). The mod's Gradle build runs TLC on every spec, so the spec and the code are checked in the same run. A parser or an overlay gets a test instead. Today the one spec is the contribution run, in [contributions](contributions.md#a-contribution-is-a-brief-run-that-ends-in-an-upload).
