# Development

## First checkout

ds-mod pins its tools with [mise](https://mise.jdx.dev). From the repository root:

```sh
mise trust
mise install
```

This gives you the pinned JDK, Go, Node and pnpm. Gradle comes from `mod/gradlew`, which fetches the rest.

## Run each part

| Part | Command | Notes |
| --- | --- | --- |
| Mod | `cd mod && ./gradlew runClient` | Starts the game with the debug build. |
| Backend | `cd backend && go run ./cmd/backend` | Listens on `:8080`. Set `ADDR` to change it. |
| Website | `cd web && pnpm install && pnpm dev` | Reads the backend at `BACKEND_URL`, `http://localhost:8080` by default. |

`runClient` signs in with your Microsoft account through DevAuth. It also loads Sodium, Mod Menu and the other mods in the `devMods` configuration. They don't ship in any jar.

In T3 Code, `t3.json` has an action for each command.

## Build the jars

`cd mod && ./gradlew build` writes two jars to `mod/build/libs/`:

- `dsmod-<version>.jar` is the release jar that players install.
- `dsmod-<version>-debug.jar` is the release jar plus the developer tools, such as capture.

## Record a capture

With the debug build, type `/dscapture` in game to start a capture, and type it again to save it. The file lands in `dsmod/captures/` in the game folder, which is `mod/run/` for `runClient`. `mod/run/` is ignored by git.

The live server has real players on it, so a person records there. Agents never connect to it.

## Add a feature

Follow [mod/AGENTS.md](../../mod/AGENTS.md#features). A feature needs a fixture cut from a capture, so record the capture first. Then cut it, with the window in seconds from the capture's start:

```sh
cd mod
./gradlew cutFixture --args="run/dsmod/captures/<capture>.jsonl golem-status live-cycle 140-200 --info Area,Protector"
```

`--types` picks the events to keep (`system,tablist` by default, and `container` brings its slots), `--info` names the tab list's info rows the feature reads so that only a change to one keeps a tab list, and `--system` keeps only the system lines that match a regex. The command redacts player names, typed chat and skins.

## Checks

`mise run check` must pass. It runs `check:mod`, `check:backend`, `check:web` and `check:docs`, and you can run each one alone.

When you change `edge`, a screen or the build, also run the game tests:

```sh
cd mod
./gradlew runClientGameTest runProdClientGameTest
```

Each opens a game window for a few seconds. The second one loads the built jars the way a player does.
