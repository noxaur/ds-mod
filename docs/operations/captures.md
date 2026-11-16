# Captures

Recording what the server shows, asking players to record it, and cutting a fixture from a recording. Why the recorder and the redactor work as they do is in [internals/captures.md](../internals/captures.md).

The live server has real players on it, so the user does all the playing there. An agent starts the client and the user joins.

## Record a capture

With the debug build, type `/dscapture` in game to start a capture, and type it again to save it. The file lands in `dsmod/captures/` in the game folder, which is `mod/run/` for `runClient`. `mod/run/` is ignored by git.

## Record from a brief

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

The window's title starts with the brief's name, and the steps sit in the top right corner of the screen. The user presses the key the HUD names to start, which starts the capture, and again after each step. It is `]` on a US layout, and Next Recording Step in the game's controls. A brief launched this way has no time limit, and its capture stays in the game folder.

After the last step the capture is saved and the HUD names its file, so the user closes the game and the command returns. The capture is the newest file in `mod/run/dsmod/captures/`, and a `step` line marks where each step began. To abandon a run, the user types `/dsmod cancel`, which deletes the capture the brief started.

## Ask players for a capture

When the user's account cannot reach what a brief needs, publish the brief, and players who can reach it record it from the mod's Contribute button. One player's run proves little, so a request collects runs from several players.

`dsctl` is how an agent does it. Locally its admin token is `ADMIN_TOKEN` in `backend/.env`, which git ignores, and `dsctl` reads it from `DSMOD_ADMIN_TOKEN`. For the deployed backend, see [deployment](deployment.md#secrets).

```sh
cd backend
export DSMOD_ADMIN_TOKEN=$(sed -n 's/^ADMIN_TOKEN=//p' .env)
pnpm -s dsctl create /tmp/golem-tiers.json --area "The End" --wanted 3 --timeout 600
pnpm -s dsctl list
pnpm -s dsctl pull <request id> ../mod/run/dsmod/captures
```

`pull` writes one `.jsonl` per run, and `cutFixture` takes them like any capture. `pnpm -s dsctl` with no command lists every command and option.

The mod offers a request in its Contribute screen, and redacts, gzips and uploads the player's run. A run that outlasts the request's `--timeout` is discarded. To try the flow against a local backend, run `pnpm dev` and `dsctl create`, then `runClient`: signing in needs the user's own Minecraft account.

## Cut a fixture

Cut a fixture with `cutFixture`, never by hand. The window is in seconds from the capture's first line:

```sh
cd mod
./gradlew cutFixture --args="run/dsmod/captures/<capture>.jsonl golem-status live-cycle 140-200 --info Protector"
```

`--types` picks the events to keep (`system,tablist` by default, and `container` brings its slots). For a feature locked to an area, it always keeps each change of world and the tab list rows and the sidebar place that tell the area, at the window's start and at each change. `--info` names the other tab list info rows the feature reads, so that only a change to one keeps a tab list. `--system` keeps only the system lines that match a regex. The command redacts player names, typed chat and skins.

Run `mise run check:mod` before deleting the capture, because `FixtureLeakTest` looks for the capture's names in every fixture and has nothing to compare once the capture is gone. Then read the fixture: a name the server writes in a line no rule knows is not redacted, and the test sees neither that nor typed text.
