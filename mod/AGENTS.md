# mod

The Fabric mod, in Kotlin. Read the root `AGENTS.md` first.

- `core` holds the features and runs in plain JVM tests. `edge` is the only release code that touches Minecraft: it turns what the server sends into `GameEvent`s for `Game`, and draws what the features return.
- The server is closed and custom, so all the mod knows of the game comes from captures. Real players are on it, so the user does all the playing there.
- `src/main` is the jar players install. `src/debug` holds what only a developer or an agent needs, and `runClient` loads both.
- DragSim Utils is all rights reserved, and its source is published to be read. Learn from it what the server sends and how a feature should behave, then write ds-mod's own code and prove it with a capture. Copy no code and no data file from it. When a feature needs a table the game can't show, tell the user.
- The server decides the Minecraft version, so bump it in `gradle.properties` only when the user asks. The release jar carries no library, for the reason in the [overview](../docs/internals/overview.md#the-release-jar-carries-no-library). Ask the user before adding one: it needs shading, a `relocate` line and a `NOTICE` entry.
