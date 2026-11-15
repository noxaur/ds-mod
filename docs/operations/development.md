# Development

## First checkout

ds-mod pins its tools with [mise](https://mise.jdx.dev). From the repository root:

```sh
mise trust
mise install
```

This gives you the pinned JDK, Node and pnpm. Gradle comes from `mod/gradlew`, which fetches the rest.

## Run each part

| Part | Command | Notes |
| --- | --- | --- |
| Mod | `cd mod && ./gradlew runClient` | Starts the game with the debug build. |
| Backend | `cd backend && pnpm install && pnpm dev` | Listens on `127.0.0.1:8787` only. |
| Website | `cd web && pnpm install && pnpm dev` | Reads the backend at `BACKEND_URL`, `http://127.0.0.1:8787` by default. |

`runClient` signs in with the user's Microsoft account through DevAuth. It also loads Sodium, Mod Menu and the other mods in the `devMods` configuration. They don't ship in any jar.

In T3 Code, `t3.json` has an action for each command.

## Build the jars

`cd mod && ./gradlew build` writes two jars to `mod/build/libs/`:

- `dsmod-<version>.jar` is the release jar that players install.
- `dsmod-<version>-debug.jar` is the release jar plus the developer tools, such as `/dscapture` and `/dskit`.

## Record a capture

With the debug build, type `/dscapture` in game to start a capture, and type it again to save it. The file lands in `dsmod/captures/` in the game folder, which is `mod/run/` for `runClient`. `mod/run/` is ignored by git.

The live server has real players on it, so the user does all the playing there.

### Record from a brief

A brief tells the user what to play, in order, and names the window, so that the user can tell one agent's client from another's. Write it as JSON outside the worktree:

```json
{
  "name": "Golem tiers",
  "steps": [
    { "do": "Warp to the End" },
    { "do": "Stand still next to the Protector until it spawns", "note": "Do not hit it.", "seconds": 90 },
    { "do": "Kill it" }
  ]
}
```

`note` is shown under the step while it is the current one. A step with `seconds` counts down and moves on by itself, and any other step waits for the user. A key the brief does not know fails the launch.

Once the user has agreed, start the client with the absolute path of the brief:

```sh
cd mod && ./gradlew runClient -Pbrief=/tmp/golem-tiers.json
```

The window's title starts with the brief's name, and the steps sit in the top right corner of the screen. The user presses the key the HUD names to start, which starts the capture, and again after each step. It is `]` on a US layout, and Next Recording Step in the game's controls. After the last step the capture is saved and the HUD names its file, so the user closes the game and the command returns. The capture is the newest file in `mod/run/dsmod/captures/`, and a `step` line marks where each step began.

### Ask players for a capture

When the user's account cannot reach what a brief needs, publish the brief as a request for players who can reach it to record. One player's run proves little, so a request collects runs from several players.

`dsctl` is how an agent does it. Locally its admin token is `ADMIN_TOKEN` in `backend/.env`, which git ignores, and `dsctl` reads it from `DSMOD_ADMIN_TOKEN`. Ask the user for the deployed one and the deployed address, which goes in `DSMOD_BACKEND`.

```sh
cd backend
export DSMOD_ADMIN_TOKEN=$(sed -n 's/^ADMIN_TOKEN=//p' .env)
pnpm -s dsctl create /tmp/golem-tiers.json --area "The End" --wanted 3 --timeout 600
pnpm -s dsctl list
pnpm -s dsctl pull <request id> ../mod/run/dsmod/captures
```

`pull` writes one `.jsonl` per run, and `cutFixture` takes them like any capture. `pnpm -s dsctl` with no command lists every command and option.

### Change the backend's tables

Edit `backend/db/schema.ts`, then in `backend/`:

```sh
pnpm exec void db generate
pnpm exec void db migrate
```

Commit the SQL that `generate` writes. When `migrate` says the local database does not match, `pnpm exec void db reset` rebuilds it from the migrations.

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

The second one loads the built jars the way a player does. Both run behind the user, taking no keyboard focus and writing the game's sound to `audio.wav` in the run folder instead of the speakers. [Issue 19](https://github.com/noxaur/ds-mod/issues/19) lists the platforms where that is incomplete.

To run one game test, name it: `./gradlew runProdClientGameTest -Pgametests=PerfTest`. `PerfTest` writes its table to `build/run/prodClientGameTest/perf.md`.

## CI

`.github/workflows/ci.yml` runs on every pull request and on `main`. It runs each `check:` task in its own job, and a `perf` job that boots the built jars and runs `PerfTest`. That job posts the table as a comment on the pull request, and fails when a row is over its cap in `mod/src/gametest/resources/perf-budgets.json`.

Each job fails when it takes over 3 minutes. A run that had to download its dependencies, after a version bump, is let through and says so.

## Beta releases

`.github/workflows/beta.yml` runs every second day at 03:00 UTC. When `main` has a commit since the last release, it runs every check and every game test, and then publishes the release jar as the prerelease `v<version>-beta.<run number>`. When anything fails, nothing is published, and the run's summary shows the perf table.

To release a beta now, run it by hand:

```sh
gh workflow run beta
```
