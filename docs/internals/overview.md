# Architecture overview

ds-mod has three parts. The mod runs in the player's game. The backend is our own REST service, and the website reads from it too. One generated contract binds them. The words used here are defined in the [glossary](glossary.md).

## The mod knows the game only from packets

dragsim.co is someone else's closed server and offers no API for what happens in the game. So the mod knows only what the server sends the client, and agents work from captures of it rather than from a description of the game, which would be a guess. See [captures](captures.md).

## Logic is pure, and only the edge touches Minecraft

The mod's logic imports nothing from Minecraft or Fabric, and only the edge does. A test of the logic then runs on a plain JVM in milliseconds, with no game to boot. Letting features call Minecraft directly was rejected, because every test would then need a running game.

## The mod runs only on dragsim.co

The features that read the server read lines in dragsim.co's own words, so on another server they can only misread them, and a wrong count is saved for good. So until the player turns DragSim Only off, the logic turns every feature off away from dragsim.co, in one place rather than once per kind of feature. The edge only reports whether the player reached dragsim.co's address, decided once per connection, and DragSim Only is the one feature that check never turns off. A hardcoded IP was rejected, because it would stop matching the day the server moves.

## The debug build runs everywhere

The debug build lifts the dragsim.co gate, because the game tests and a local run play in singleplayer. Making each test turn the setting off first was rejected, because it is one more thing to forget. For the same reason the gate starts open in the logic, so a unit test or a replay never meets it.

## Every on/off setting is a feature

A setting the player turns on or off is a feature, even Fullbright, which only the edge draws, or DragSim Only, which only the logic reads. The settings, the player docs check and the tests walk the one list of features, so each gets a docs section, a README line and a test. A plain switch was rejected, because six of them skipped all three, and the settings can no longer declare one. Settings that are not on or off, such as keys, scale and theme, stay plain settings.

## Release and debug are separate builds

Developer tools live in a build that release code can't see, so the compiler keeps them out of the jar players install. A review could miss a stray tool, and the compiler can't. The recorder is the one tool that ships, for the reason in [captures](captures.md#the-recorder-ships-and-runs-only-for-a-contribution).

## The release jar carries no library

The release jar holds only ds-mod's own code, and the libraries it uses come with Minecraft and Fabric Language Kotlin, which every player installs. A bundled library would have to be relocated so it can't clash with another mod's copy, and then none of its types could cross a public signature. So a library ds-mod can't take from the platform is not used.

## The backend and the website keep each other's boundaries

The backend runs on Cloudflare and the website on Vercel, and the website calls the backend only from its own servers. The contract is generated from the backend's API definition, because a hand-written one drifts, and the mod is Kotlin and can share no types with either. See [backend](backend.md) and [sign-in](sign-in.md).

## Specs for interleavings

A state machine whose states interleave in ways a unit test can't enumerate gets a TLA+ spec, checked in the same build as the code. A parser or an overlay gets a test instead, because a spec there costs more than it finds. See [contributions](contributions.md#a-contribution-is-a-brief-run-that-ends-in-an-upload).
