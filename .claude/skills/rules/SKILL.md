---
name: rules
description: Use when a mistake happens a second time, when a check fails and an exception seems right, or when adding, changing or removing a check.
---

Every rule is a check, and its failure prints the file, the line and the fix. Prose holds only a judgment call that no check can make.

## Stop a repeated mistake at the highest level that works

1. Change the structure so the mistake cannot be written, as the source sets do.
2. Use a type that rejects it.
3. Add a lint rule. In `mod/` that is an instance in `detekt.yml`, named `<Rule>/<id>`: a built-in such as `ForbiddenMethodCall`, or one of ours in `mod/lint/` (`ForbiddenReference`, `ForbiddenText` and the rest), with the files it covers, a `reason` that says what to write instead, `examples` it must flag and `counterExamples` it must pass where legitimate code sits close. A rule on what compiled classes may call is an ArchUnit test in `src/test/…/rules/`, with a fixture class it must flag. In `web/` it is `eslint.config.mjs`, and in `backend/` it is `lint/dsmod.js`, each with a message that says what to write instead.
4. Add a test, whose failure says what to write instead.
5. Write it in an `AGENTS.md` or a skill, only for a judgment call that no check can make.

Then add a row to the table below. If a row has nothing enforcing it, enforce it in the same change. Once a check enforces a rule, delete the prose that states it, because the check's message carries the fix. Delete a rule once the structure makes the mistake impossible.

## Exceptions

Only for an exception the user agreed to: `@Suppress("<Rule>/<id>")` on the declaration in `mod/`, `rule:clone <reason>` at the end of the line for `ClonesTest`, or `eslint-disable-next-line <rule> -- <reason>` in `web/`. A comment the user agreed to keep starts with `@keep `.

## The rules

A commit runs all of them, except the game tests, which a pull request runs, and the CI rules.

| Rule | Enforced by |
| --- | --- |
| No code, config or spec holds a comment, but an exception the user agreed to | `scripts/comments.ts` against `comments.txt`, in `check:comments` |
| `docs/internals/` names no code, and holds one short paragraph per decision | `check:docs` in `mise.toml` |
| `core` names nothing from Minecraft, Fabric, Mojang or `edge`, even by full name | `PurityTest` reads the compiled classes |
| The edge is handed what it uses by `DsModClient`, the one place that wires the mod, and only the mixins and the entrypoints reach `Hooks` | `OwnersTest` reads the compiled classes |
| Each place the release build sends or refuses input for the player is approved | Only `edge/input` and mixins touch input, and `InputSitesTest` checks the `@InputApproved` on each gate and caller. The agent asks the user before it keeps a site that sends, refuses or triggers anything new |
| The release build runs only on dragsim.co, unless the player turns DragSim Only off | `Game` gates every feature in `core`, `SwitchesTest` makes the edge ask `Feature.runs`, and `GameTest`, `DragSimTest` and the game test `DragSimOnlyTest` |
| A feature fires only in the area it names | `Feature` takes an `Area` and `Game` gates on it. `FeatureReplayTest` replays each fixture outside the area |
| A feature reads no area for itself, from the tab list or the sidebar, and sits in its area's folder | `InfoRows` hands a feature no place and refuses an area row, `area-by-declaration` in `mod/detekt.yml` for a place named in a feature, and `RulesTest` |
| A feature tells the player's own kills through one part | `OwnKills`, which keeps `CombatXp` private |
| A feature marks slots and reads a container again after a change through one layer | `SlotMarks` and `ContainerReader`, and `container-by-reader` in `mod/detekt.yml` for a handler that only sets a flag |
| A feature never calls into a part from its own handler of an event the part hears for itself | `Feature.on` runs every handler of a type, so a part subscribes itself and keeps its own handlers private, and `part-by-itself` in `mod/detekt.yml` for `SlotMarks.invalidate` |
| A feature follows the player's active pet through one part | `CurrentPet`, which keeps the readers of a pet's stand, head and card private, and `pet-by-current-pet` in `mod/detekt.yml` for the sidebar's `Pet:` line |
| The mod's text and colors come from its text layer | `TextLayerTest` reads the compiled classes, and `text-by-role` in `mod/detekt.yml` for a hex color or a `§` |
| A feature writes a number, a rate or a time, and reads a number the server wrote, through `core.text` | `number-by-helper`, `rate-by-helper` and `count-by-helper` in `mod/detekt.yml` |
| A feature reads a chat line by its start, and a line that starts right and fails to parse is counted | `line` in `Feature`, because `Feature.on` takes only a `FeatureEvent` and `SystemMessage` is none |
| A mod test builds server text, reads a readout and reads a test resource through the harness in `mod/src/test/…/harness/` | `server-text-by-harness`, `readout-by-harness` and `resource-by-harness` in `mod/detekt.yml` |
| A feature reads a slayer boss line and the Protector's stage through their parsers in `core/game` | `SlayerLine` and `ProtectorStage`, and `slayer-by-line` and `protector-by-stage` in `mod/detekt.yml` |
| Which slots of an open container are the player's, and where the inventory holds them, is named once | `ContainerState`'s `menuSlots`, `playerSlots` and inventory slots, and `player-slots-by-container` and `player-rows-by-container` in `mod/detekt.yml` |
| The mod holds no `!!`, so each null is said as a `?: return`, a smart cast or a `checkNotNull` that gives its reason | `no-bang-bang` in `mod/detekt.yml` |
| A feature reads time from the `now` its handler receives, never the wall clock | `no-wall-clock-in-core` in `mod/detekt.yml` |
| No catch block is empty | `no-swallowed-catch` in `mod/detekt.yml` |
| The edge reads a stack's SkyBlock data in one place, which reads each item's tag once | `item-data-by-hand` in `mod/detekt.yml` |
| The edge looks up an item or an entity type by its registry id in one place | `registry-by-hand` in `mod/detekt.yml` |
| Core reads an item's lore and data through `Item`, which strips the server's glyphs once per item | `lore-glyphs-by-hand` and `item-json-by-hand` in `mod/detekt.yml` |
| Every command and chat line the mod sends for the player goes through one sender | `send-through-player-commands` in `mod/detekt.yml` |
| The mod's shapes are drawn by its kit, in the player's theme | `KitTest` reads the compiled classes |
| Each lint rule in `mod/detekt.yml` flags its examples and passes its counter-examples | `DetektConfigTest` in `mod/lint/` |
| Every public release member has a caller in the release or debug code, so the jar carries no dead code and no game-test hook | `CallersTest` in `mod/…/rules/` reads the compiled classes |
| The mod's code and its tests are not copied with a value or two changed | `ClonesTest` in `mod/…/rules/` finds a repeated block and a near-identical line in every source set |
| Release code cannot use debug tools | The `main` and `debug` source sets |
| Raw captures stay out of git | They are written to `mod/run/`, which is git-ignored |
| A fixture holds no player name from a capture on this machine, and an alias wherever a player's name stands | `FixtureLeakTest`, and `cutFixture` refuses such a fixture while the captures are still there |
| Every feature shows what its transcript says | `FeatureReplayTest` replays each feature's fixtures |
| A feature that reads nothing from the server has a game test | `FeatureReplayTest` |
| A feature inside a group runs only while its group is on, and asks only for itself | `Feature` reads the switch above it in the tree, and a group's class is private to its file, so no code can ask for it |
| Every feature class is placed in `SettingsTree` | `FeaturesListedTest` reads the compiled classes |
| Every on/off setting is a feature | `SettingsTree` refuses a switch outside a feature when it is built |
| Every feature has a section in the player docs | `RulesTest` |
| Every feature has a one-line entry in the README | `RulesTest` |
| Every saved setting, a feature's saved key included, has exactly one row in the settings tree, or is listed as stored only with a reason, and every row has its own id, a Title Case name of three words or fewer and a one-sentence description | `SettingsTreeTest` |
| The kit gallery shows every kind of settings row and every look a pick draws | `KitRowsTest` |
| A setting's description in `SettingsTree` is short enough that the settings screen shows it at full size | `RulesTest` |
| A saved setting keeps its key in the config file, so a player who updates keeps what they set | `ConfigFileTest` loads a file 0.1.0 wrote and checks every value, and loads a file main wrote with every row changed and saves it again unchanged |
| Every setting is saved in a slot of a store, under a key written where it is declared | A row takes only a `Bind`, which a `Slot` makes, and `SettingsTreeTest` refuses a field of `DsModConfig` besides favorites |
| A feature's saved state is immutable and has a default for every field, so a change is written and an older save loads | `SavedStateTest` walks every feature's `state` |
| A feature's handler and readout average under 20µs | `FeatureReplayTest` |
| A capture type has one producer | `CaptureWriter` rejects a raw record with an event's type |
| The jars a player installs boot and open settings | `runProdClientGameTest` |
| A unit test run takes at most 30 seconds | `timeout` on `tasks.test` in `mod/build.gradle.kts`, and `scripts/test-time-limit.ts`, the global setup of the tests in `backend/vite.config.ts` and `web/vite.config.ts` |
| A game test passes whichever tests ran before it | `FreshStart` in `mod/src/gametest/` starts the mod afresh before each test |
| A game test takes at most 20 seconds, its world included | `TestClock` in `mod/src/gametest/` |
| A game test moves the mouse, clicks and opens the inventory through `Pointer.kt`, in GUI pixels | `gametest-point-by-pointer` and `gametest-inventory-by-pointer` in `mod/detekt.yml` |
| A game test switches the theme through `Themes.kt`, which puts it back, and reads a screenshot's pixels through `Pixels.kt` | `gametest-theme-by-intheme` and `gametest-pixels-by-pixels` in `mod/detekt.yml` |
| A game test reads the clicks a step sent only from that step | `ClickSpy.during` in `mod/src/gametest/`, which keeps the record private |
| A game test's wait fails the test when it times out | `gametest-no-swallowed-wait` and `gametest-poll-by-waitfor` in `mod/detekt.yml`, and `waitUntil` in `mod/src/gametest/Waits.kt` |
| A game test finds a widget through `ServerMenus` | `gametest-widget-by-servermenus` and `gametest-children-by-screenwidgets` in `mod/detekt.yml` |
| A game test builds its player, a still world and the dragon timer from shared scenes | `gametest-player-by-scenes`, `gametest-frozen-by-scenes` and `gametest-egg-by-the-end` in `mod/detekt.yml` |
| A commit passes every project's checks | `.githooks/pre-commit`, which `mise install` installs |
| A pull request runs the game tests its change reaches | CI's `gametests` job, and `GameTestPick.kt` in `mod/src/debug/`, which leaves `PerfTest` to CI's `perf` job |
| Every file of `edge` and `debug` reaches a game test | `GameTestPickTest` |
| A CI job takes at most 3 minutes | `.github/actions/time-limit`, the last step of every job in `ci.yml` |
| The mod stays inside its frame, tick, packet and size caps | `PerfTest` against `mod/src/gametest/resources/perf-budgets.json`, in CI's `perf` job, and `tick-by-ticks` in `mod/detekt.yml` for a tick handler the tick cap would miss |
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
| Every method of the stores in `backend/routes/_store.ts` has a case that runs it on the real migrations | `suites` in `backend/test/store.test.ts`, keyed by each store's methods so a missing case fails `tsc` |
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
