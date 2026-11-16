---
name: feature
description: Use when adding or changing a ds-mod feature in `core/feature/`, its fixture or its transcript.
---

A feature is one class in `core/feature/<area>/`. `Feature`'s KDoc names the feature to start from, and why it is built this way is in [internals/features.md](../../../../docs/internals/features.md).

Load the `ui` skill too when the feature shows the player anything.

## Server lines

- Match `ServerText.plain` anchored at the start of the line, because a player can type anything after their name.
- A line that reaches the feature's branch and then fails to parse goes to `unparsed(text)` as the server sent it. That is how a format change on the server shows up.
- Two features that read the same line share its parser in `core/game`, as drops share `Drop.parse`.

## State

- State that outlives the session is one `@Serializable` class given to `saved()`. Replace its `value` to change it.

## Fixtures

Record the capture with the `captures` skill, and cut the fixture as [Cut a fixture](../../../../docs/operations/captures.md#cut-a-fixture) says.

- Keep the lines the feature must ignore, which prove it ignores them.
- Keep the tab list for a feature locked to an area, and the sidebar with `--types ..,sidebar` when the sidebar tells the area. Without them the fixture replays as if the player never left.
- When a name in a fixture slipped through, or a word was taken for one, teach `PlayerNames` from a capture that shows the line. It is the one place that knows where names sit.
- Write a game test when you add to `edge`. A feature that only reads events and returns readouts is covered by its replay.
