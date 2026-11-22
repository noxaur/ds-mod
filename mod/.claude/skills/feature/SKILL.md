---
name: feature
description: Use when adding or changing a ds-mod feature in `core/feature/`, its fixture or its transcript.
---

A feature is one class in `core/feature/<area>/` that implements `HasFeature` and holds the `Feature` it builds, as `override val feature = Feature(...)`, declared first. Behaviour two features share is a part each one holds, as `BossMarks`, never a base class. `Feature`'s KDoc names the feature to start from, and why it is built this way is in [internals/features.md](../../../../docs/internals/features.md).

Load the `ui` skill too when the feature shows the player anything.

## Settings

- The switch's description and default go in the `Feature(..)` call, with the keys older versions saved it under as `formerly`, and so do the `hud`, `slots` and `marks` it shows, as `hud = ::hud`. Every other setting is one line in the class, as `val inFight = feature.switch("beamInFight", "During the Fight", "Keep the beam while the Protector is up.", default = false)`, read as `inFight.value`. The first argument is the key in the config file, under the feature's `Store`. The kinds are in [`settings.md`](../ui/settings.md) beside the `ui` skill.
- Place the feature once in `SettingsTree`: `group(Feature(c))` for a group of its own, or `feature(Feature(c))` among others. That is also what lists it.
- The edge reads a setting from the feature `DsModClient` hands it, as `slotLock.row.value`, and asks `slotLock.feature.runs` for the switch, never `enabled` or `switch`. `SwitchesTest` fails on a read.

## Server lines

- Text other than a chat line, such as a tab row or lore, that reaches the feature's branch and then fails to parse goes to `unparsed(text)` as the server sent it. That is how a format change on the server shows up. A chat line's handler, `line`, counts its own.
- Two features that read the same line share its parser in `core/game`, as drops share `Drop.parse`.

## State

- State that outlives the session is one `@Serializable` class given to `saved()`. Replace its `value` to change it.
- State that holds for one world is declared as `private var phase by feature.world { Phase.Unknown }`, and for one session on the server with `feature.session { }`. `Game` resets it, so never reset it in a `WorldChanged` or `LeftServer` handler. Values that exclude each other, such as a dragon alive and an egg returning, are one sealed type, as in `DragonTimer`.

## Fixtures

Record the capture with the `captures` skill, and cut the fixture as [Cut a fixture](../../../../docs/operations/captures.md#cut-a-fixture) says.

- Keep the lines the feature must ignore, which prove it ignores them.
- When a name in a fixture slipped through, or a word was taken for one, teach `PlayerNames` from a capture that shows the line. It is the one place that knows where names sit.
