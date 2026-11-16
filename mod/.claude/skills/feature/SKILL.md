---
name: feature
description: Use when adding or changing a ds-mod feature in `core/feature/`, its fixture or its transcript.
---

A feature is one class in `core/feature/<area>/`, listed in `Features.kt`, with its switch in `DsModConfig` and `SettingsTree`. Why it is built this way is in [internals/features.md](../../../../docs/internals/features.md). Copy the feature closest to yours:

| To | Copy |
| --- | --- |
| Follow lines in chat | `DragonTimer` |
| Read the tab list's info rows, or the sidebar's the same way | `GolemStatus` |
| Read items and mark slots | `RarityMarks` |
| Show items on the HUD, from the player's own inventory or an entity near them | `EquipmentDisplay` |
| Change how the game draws, reading nothing from the server | `Fullbright` |
| Count something and keep the total | `SlayerTracker` |
| Hide a line the server sends, and tell the player once that you did | `AbilitySpam` |
| Tell the player something once, and read a setting besides the switch | `LootAlerts` |
| Track a rate, and follow the player into and out of its area | `ZealotTracker` |
| Mark a place in the world | `BossBeacon` |
| Act on a key the player binds, or change the camera | `FreeLook` and `Camera`, with `Cameras` in `edge` and the rules in `core/camera` |
| Handle the player's own input on a vanilla screen, reading nothing from the server | `CopyChat`, with `CopyChats` in `edge` |

Load the `ui` skill too when the feature shows the player anything.

## In and out

- **In:** `GameEvent`s, and the shared `tab`, `sidebar` and `container`. `info` gives the `Key: value` rows of the tab list and the sidebar. A feature that needs another part of either extends `InfoRows`.
- **Out:** what `Feature` declares. A feature that hides a line by default tells the player once and names its setting.

## Areas

Pass the feature's `Area` to `Feature(..)`. For a new place, add an `Area` entry with the tab list row or the sidebar place a capture shows, preferring whichever arrives first. A dungeon, the Dungeon Hub and a part of an area, such as the Void Sepulture, are told by the sidebar's `⏣` line.

## Server lines

- Match `ServerText.plain` anchored at the start of the line, because a player can type anything after their name.
- A line that reaches the feature's branch and then fails to parse goes to `unparsed(text)` as the server sent it. That is how a format change on the server shows up.
- Two features that read the same line share its parser in `core/game`, as drops share `Drop.parse`.

## State

- State that outlives the session is one immutable `@Serializable` class given to `saved()`, with a default for every field. Replace its `value` to change it, because a change made in place is never written.
- Clear what belongs to one world on `WorldChanged`. `Game` runs that handler too when the player switches the feature off.

## Fixtures

Record the capture with the `captures` skill, and cut the fixture as [Cut a fixture](../../../../docs/operations/captures.md#cut-a-fixture) says.

- Keep the lines the feature must ignore, which prove it ignores them.
- Keep the tab list for a feature locked to an area, and the sidebar with `--types ..,sidebar` when the sidebar tells the area. Without them the fixture replays as if the player never left.
- When a name in a fixture slipped through, or a word was taken for one, teach `PlayerNames` from a capture that shows the line. It is the one place that knows where names sit.
- Write a game test when you add to `edge`. A feature that only reads events and returns readouts is covered by its replay.
