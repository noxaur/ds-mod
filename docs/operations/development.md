# Development

## First checkout

ds-mod pins its tools with [mise](https://mise.jdx.dev). From the repository root:

```sh
mise trust
mise install
```

This gives the pinned JDK, Node and pnpm. Gradle comes from `mod/gradlew`, which fetches the rest.

## Run each part

| Part | Command | Notes |
| --- | --- | --- |
| Mod | `cd mod && ./gradlew runClient` | Starts the game with the debug build. |
| Backend | `cd backend && pnpm install && pnpm dev` | Listens on `127.0.0.1:8787` only. |
| Website | `cd web && pnpm install && pnpm dev` | Reads the backend at `BACKEND_URL`, `http://127.0.0.1:8787` by default. |

`runClient` signs in with the user's Microsoft account through DevAuth. It also loads Sodium, Mod Menu and the other mods in the `devMods` configuration. They don't ship in any jar.

Before the game starts, `runClient` copies each file in `mod/run-defaults/` into `mod/run/` that `mod/run/` lacks, so a fresh checkout starts with the user's dev settings and a server list that holds DragSim. To change a default, edit the file in `mod/run-defaults/`. It also puts DragSim's resource pack where the game looks for it, from a copy in `~/.gradle/caches/dsmod/` that it refreshes when the server's pack is newer. The debug build's title screen has a Join DragSim button above Singleplayer.

In T3 Code, `t3.json` has an action for each command.

## Build the jars

`cd mod && ./gradlew build` writes two jars to `mod/build/libs/`:

- `dsmod-<version>.jar` is the release jar that players install.
- `dsmod-<version>-debug.jar` is the release jar plus the developer tools, such as `/dscapture` and `/dskit`.

## Change the backend's tables

Edit `backend/db/schema.ts`, then in `backend/`:

```sh
pnpm exec void db generate
pnpm exec void db migrate
```

Commit the SQL that `generate` writes. When `migrate` says the local database does not match, `pnpm exec void db reset` rebuilds it from the migrations. A deploy applies the migrations, as [deployment](deployment.md#deploy-the-backend) describes.

## Add a feature

Follow [mod/AGENTS.md](../../mod/AGENTS.md#features). A feature needs a fixture cut from a capture, so record the capture first, as [captures](captures.md) describes, and cut the fixture from it.

## Checks

`mise run check` must pass. It runs `check:mod`, `check:backend`, `check:web` and `check:docs`, and each one runs alone too.

When a change touches `edge` or a screen, also run the game tests that cover it. A run names its tests, and one that names none fails:

```sh
cd mod
./gradlew runClientGameTest -Pgametests=ChatTest,KitTest
```

When a change touches the build, run `./gradlew runProdClientGameTest -Pgametests=SettingsScreenTest`, which loads the built jars the way a player does. Both run behind the user, taking no keyboard focus and writing the game's sound to `audio.wav` in the run folder instead of the speakers. [Issue 19](https://github.com/noxaur/ds-mod/issues/19) lists the platforms where that is incomplete.

One test takes about 30 seconds, most of it booting the game. The whole suite takes minutes, so the beta pipeline runs it, and `-Pgametests=all` runs it here. Each test's time is in the log as `TestClock:`, and a test over 20 seconds fails the run. `PerfTest` writes its table to `build/run/prodClientGameTest/perf.md`.

To see a screen, write a game test that opens it and calls `takeScreenshot`. The image lands in `mod/build/run/clientGameTest/screenshots/`.
