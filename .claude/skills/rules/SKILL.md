---
name: rules
description: Use when a mistake happens a second time, when a check fails and an exception seems right, or when adding, changing or removing a check.
---

Every rule is a check, and its failure prints the file, the line and the fix. Prose holds only a judgment call that no check can make.

## Stop a repeated mistake at the highest level that works

1. Change the structure so the mistake cannot be written, as the source sets do.
2. Use a type that rejects it.
3. Add a lint rule. In `mod/` that is `src/test/kotlin/io/github/noxaur/dsmod/rules/Rules.kt`: a pattern that must not appear in some files, a `fix` that says what to write instead, an `example` line it must catch, and `counterExamples` it must pass where legitimate code sits close. In `web/` it is `eslint.config.mjs`, and in `backend/` it is `lint/dsmod.js`, each with a message that says what to write instead.
4. Add a test, whose failure says what to write instead.
5. Write it in an `AGENTS.md` or a skill, only for a judgment call that no check can make.

Then add a row to the table below. If a row has nothing enforcing it, enforce it in the same change. Once a check enforces a rule, delete the prose that states it, because the check's message carries the fix. Delete a rule once the structure makes the mistake impossible.

## Exceptions

Only for an exception the user agreed to, end the line with a comment: `rule:<id> <reason>` in `mod/`, or `eslint-disable-next-line <rule> -- <reason>` in `web/`. A comment the user agreed to keep starts with `@keep `.

## The rules

A commit runs all of them, except the game tests, which a pull request runs, and the CI rules.

| Rule | Enforced by |
| --- | --- |
| No code, config or spec holds a comment, but an exception the user agreed to | `scripts/comments.ts` against `comments.txt`, in `check:comments` |
| `docs/internals/` names no code, and holds one short paragraph per decision | `check:docs` in `mise.toml` |
| `core` names nothing from Minecraft, Fabric, Mojang or `edge`, even by full name | `PurityTest` reads the compiled classes |
| Each place the release build sends or refuses input for the player is approved | `InputSitesTest` against `mod/input-sites.txt`, and the agent asks the user before it keeps a site that sends, refuses or triggers anything new |
| The release build runs only on dragsim.co, unless the player turns DragSim Only off | `Game` gates every feature in `core`, `SwitchesTest` makes the edge ask `Feature.runs`, and `GameTest`, `DragSimTest` and the game test `DragSimOnlyTest` |
| A feature fires only in the area it names | `Feature` takes an `Area` and `Game` gates on it. `FeatureReplayTest` replays each fixture outside the area |
| A feature reads no area for itself, from the tab list or the sidebar, and sits in its area's folder | `InfoRows` hands a feature no place and refuses an area row, `area-by-declaration` in `mod/…/Rules.kt` for a place named in a feature, and `RulesTest` |
| A feature tells the player's own kills through one part | `OwnKills`, and `kills-by-own-kills` in `mod/…/Rules.kt` for Combat XP read anywhere else |
| A feature marks slots and reads a container again after a change through one layer | `SlotMarks` and `ContainerReader`, and `container-by-reader` in `mod/…/Rules.kt` for a handler that only sets a flag |
| A feature never calls into a part from its own handler of an event the part hears for itself | `Feature.on` runs every handler of a type, so a part subscribes itself, and `part-by-itself` in `mod/…/Rules.kt` |
| A feature follows the player's active pet through one part | `CurrentPet`, and `pet-by-current-pet` in `mod/…/Rules.kt` for a pet read anywhere else |
| The mod's text and colors come from its text layer | `TextLayerTest` reads the compiled classes, and `text-by-role` in `mod/…/Rules.kt` for a hex color or a `§` |
| A feature writes a number, a rate or a time, and reads a number the server wrote, through `core.text` | `number-by-helper`, `rate-by-helper` and `count-by-helper` in `mod/…/Rules.kt` |
| A feature reads a chat line by its start, and a line that starts right and fails to parse is counted | `line` in `Feature`, and `chat-by-line` in `mod/…/Rules.kt` for a chat handler written by hand |
| A mod test builds server text, reads a readout and reads a test resource through the harness in `mod/src/test/…/harness/` | `server-text-by-harness`, `readout-by-harness` and `resource-by-harness` in `mod/…/Rules.kt` |
| A feature reads a slayer boss line and the Protector's stage through their parsers in `core/game` | `SlayerLine` and `ProtectorStage`, and `slayer-by-line` and `protector-by-stage` in `mod/…/Rules.kt` |
| Which slots of an open container are the player's, and where the inventory holds them, is named once | `ContainerState`'s `menuSlots`, `playerSlots` and inventory slots, and `player-slots-by-container` and `player-rows-by-container` in `mod/…/Rules.kt` |
| The mod holds no `!!`, so each null is said as a `?: return`, a smart cast or a `checkNotNull` that gives its reason | `no-bang-bang` in `mod/…/Rules.kt` |
| A feature reads time from the `now` its handler receives, never the wall clock | `no-wall-clock-in-core` in `mod/…/Rules.kt` |
| No catch block is empty | `no-swallowed-catch` in `mod/…/Rules.kt` |
| The edge reads a stack's SkyBlock data in one place, which reads each item's tag once | `item-data-by-hand` in `mod/…/Rules.kt` |
| The edge looks up an item or an entity type by its registry id in one place | `registry-by-hand` in `mod/…/Rules.kt` |
| Core reads an item's lore and data through `Item`, which strips the server's glyphs once per item | `lore-glyphs-by-hand` and `item-json-by-hand` in `mod/…/Rules.kt` |
| Every command and chat line the mod sends for the player goes through one sender | `send-through-player-commands` in `mod/…/Rules.kt` |
| The mod's shapes are drawn by its kit, in the player's theme | `KitTest` reads the compiled classes |
| The mod's code is not copied with a value or two changed | `ClonesTest` in `mod/…/rules/` finds a repeated block and a near-identical line |
| Release code cannot use debug tools | The `main` and `debug` source sets |
| Raw captures stay out of git | They are written to `mod/run/`, which is git-ignored |
| A fixture holds no player name from a capture on this machine, and an alias wherever a player's name stands | `FixtureLeakTest`, and `cutFixture` refuses such a fixture while the captures are still there |
| Every feature shows what its transcript says | `FeatureReplayTest` replays each feature's fixtures |
| A feature that reads nothing from the server has a game test | `FeatureReplayTest` |
| A feature inside a group runs only while its group is on, and asks only for itself | `Feature` reads the switch above it in the tree, and `group-by-tree` in `mod/…/Rules.kt` |
| Every feature class is placed in `SettingsTree` | `FeaturesListedTest` reads the compiled classes |
| Every on/off setting is a feature | `SettingsTree` refuses a switch outside a feature when it is built |
| Every feature has a section in the player docs | `RulesTest` |
| Every feature has a one-line entry in the README | `RulesTest` |
| Every saved setting, a feature's saved key included, has exactly one row in the settings tree, or is listed as stored only with a reason, and every row has its own id, a Title Case name of three words or fewer and a one-sentence description | `SettingsTreeTest` |
| The kit gallery shows every kind of settings row and every look a pick draws | `KitTest` |
| A setting's description in `SettingsTree` is short enough that the settings screen shows it at full size | `RulesTest` |
| A saved setting keeps its key in the config file, so a player who updates keeps what they set | `ConfigFileTest` loads a file 0.1.0 wrote and checks every value |
| A feature's saved state is immutable and has a default for every field, so a change is written and an older save loads | `SavedStateTest` walks every feature's `state` |
| A feature's handler and readout average under 20µs | `FeatureReplayTest` |
| A capture type has one producer | `CaptureWriter` rejects a raw record with an event's type |
| The jars a player installs boot and open settings | `runProdClientGameTest` |
| A unit test run takes at most 30 seconds | `timeout` on `tasks.test` in `mod/build.gradle.kts`, and `scripts/test-time-limit.ts`, the global setup of the tests in `backend/vite.config.ts` and `web/vite.config.ts` |
| A game test passes whichever tests ran before it | `FreshStart` in `mod/src/gametest/` starts the mod afresh before each test |
| A game test takes at most 20 seconds, its world included | `TestClock` in `mod/src/gametest/` |
| A game test moves the mouse, clicks and opens the inventory through `Pointer.kt`, in GUI pixels | `gametest-point-by-pointer` and `gametest-inventory-by-pointer` in `mod/…/Rules.kt` |
| A game test switches the theme through `Themes.kt`, which puts it back, and reads a screenshot's pixels through `Pixels.kt` | `gametest-theme-by-intheme` and `gametest-pixels-by-pixels` in `mod/…/Rules.kt` |
| A game test reads the clicks a step sent only from that step | `ClickSpy.during` in `mod/src/gametest/`, which keeps the record private |
| A game test's wait fails the test when it times out | `gametest-no-swallowed-wait` and `gametest-poll-by-waitfor` in `mod/…/Rules.kt`, and `waitUntil` in `mod/src/gametest/Waits.kt` |
| A game test finds a widget through `ServerMenus` | `gametest-widget-by-servermenus` and `gametest-children-by-screenwidgets` in `mod/…/Rules.kt` |
| A game test builds its player, a still world and the dragon timer from shared scenes | `gametest-player-by-scenes`, `gametest-frozen-by-scenes` and `gametest-egg-by-the-end` in `mod/…/Rules.kt` |
| A commit passes every project's checks | `.githooks/pre-commit`, which `mise install` installs |
| A pull request runs the game tests its change reaches | CI's `gametests` job, and `GameTestPick.kt` in `mod/src/debug/` |
| Every file of `edge` and `debug` reaches a game test | `GameTestPickTest` |
| A CI job takes at most 3 minutes | `.github/actions/time-limit`, the last step of every job in `ci.yml` |
| The mod stays inside its frame, tick and size caps | `PerfTest` against `mod/src/gametest/resources/perf-budgets.json`, in CI's `perf` job |
| A beta ships only when every check and game test passes | `.github/workflows/beta.yml` |
| Every capture type reaches the file | `CaptureTest`, and `BriefTest` for `step` |
| A redacted capture holds no player name the mod knows, and no typed chat | `RedactCaptureTest` |
| The backend believes a sign-in only when Mojang confirms it | `minecraft.test.ts` in `backend/src/identity/` |
| A player cannot reach an agent's routes, and an agent never sees who recorded a run | `app.test.ts` in `backend/src/` |
| The website signs a browser in only after the player confirms whose account it is | `signIn.test.ts` in `web/app/login/` |
| Specs hold their invariants | TLC on `specs/` |
| `api/openapi.json` matches the backend's API definition | `app.test.ts` in `backend/src/` |
| Backend code is formatted, type-safe and passes lint with no warnings | `vp check` in `check:backend` |
| Backend failures are modeled with Effect, not `try` | `dsmod/no-try` in `backend/lint/dsmod.js` |
| The backend logs through a named `console` call, never `console[level]` | `dsmod/no-computed-console` in `backend/lint/dsmod.js` |
| The backend has no `any`, and reads JSON through a `Schema` outside its tests | `typescript/no-explicit-any` in `backend/vite.config.ts`, and `dsmod/no-json-parse` in `backend/lint/dsmod.js` |
| Backend code reads the time through Effect's `Clock` and makes ids with `src/ids.ts`, so a test sets the time with `TestClock` | `dsmod/time-through-effect` in `backend/lint/dsmod.js` |
| Backend tests build the app, sign-in, a caller and a gzipped capture from one harness | `dsmod/test-harness` in `backend/lint/dsmod.js`, which sends a test to `backend/test/harness.ts` |
| `vite` is the Vite+ core of `vite-plus`'s version, so Void and Vite+ share one Vite | `vite.test.ts` in `backend/test/` |
| `backend/src/` imports nothing from Void | `no-restricted-imports` in `backend/vite.config.ts` |
| Backend code outside `items/` reads dragsim.co's data through `src/upstream/` and the item renderer only through `items/render.ts`, and profiles reaches items only through its model and that entry | `no-restricted-imports` in `backend/vite.config.ts` |
| The memory stores and `routes/_store.ts` pass one contract, which has a case for every `Store` method | `storeContract` in `backend/test/contract.ts`, keyed by each store's methods so a missing case fails `tsc` |
| Backend dependencies are at least 14 days old | `minimumReleaseAge` in `backend/pnpm-workspace.yaml` |
| The website is type-safe | `tsc` with `strict` and the extra flags in `web/tsconfig.json` |
| Website code is formatted | `vp fmt --check` in `web/package.json` |
| Website code passes strict lint with no warnings | `typescript-eslint` strict-type-checked in `web/eslint.config.mjs` |
| Website failures are modeled with Effect, not `try` | `no-restricted-syntax` in `web/eslint.config.mjs` |
| The website logs through a named `console` call, never `console[level]` | `no-restricted-syntax` in `web/eslint.config.mjs` |
| The website has no `any`, and reads JSON through a `Schema` outside its tests | `@typescript-eslint/no-explicit-any` from strict-type-checked, and `no-restricted-syntax` in `web/eslint.config.mjs` |
| The website reads `process.env` in `web/app/env.ts` alone | `no-restricted-syntax` in `web/eslint.config.mjs` |
| Every interactive control comes from ui-sdk | `react/forbid-elements` and `no-restricted-imports` in `web/eslint.config.mjs` |
| Web dependencies are at least 14 days old | `minimumReleaseAge` in `web/pnpm-workspace.yaml` |
| `docs/README.md` links every page in `docs/` | `check:docs` |
| `docs/internals/` records why, not steps | `check:docs` rejects a recipe heading |
| `docs/operations/` addresses an agent, who owns no machine or account | `check:docs` rejects "your" |
