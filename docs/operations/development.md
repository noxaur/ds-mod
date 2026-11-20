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
| Backend | `cd backend && pnpm install && pnpm dev` | Listens on `127.0.0.1:16385` only. |
| Website | `cd web && pnpm install && pnpm dev` | Reads the backend at `BACKEND_URL`, `http://127.0.0.1:16385` by default. |

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

Follow the [`feature` skill](../../mod/.claude/skills/feature/SKILL.md). A feature needs a fixture cut from a capture, so record the capture first, as [captures](captures.md) describes, and cut the fixture from it.

## Checks

A commit runs `mise run check`, which runs `check:mod`, `check:backend`, `check:web` and `check:docs`. `mise install` points git at `.githooks/`, whose `pre-commit` runs it, and a failure prints the log and commits nothing.

A pull request's `gametests` job runs the game tests that its change reaches, against the jars a player installs, and `GameTestPick.kt` in `mod/src/debug/` says how a change reaches a test. Each test's time is in the log as `TestClock:`, and a test over 20 seconds fails the run. The beta pipeline runs every game test.

To see a screen, write a game test that opens it and calls `takeScreenshot`. The job uploads its log and screenshots as the `gametests` artifact, which `gh run download <run id> -n gametests` fetches.
