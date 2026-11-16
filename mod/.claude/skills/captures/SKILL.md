---
name: captures
description: Use when reading a capture, when a task needs game data no capture shows, or when changing the recorder or a capture type.
---

A capture is a JSONL file of what the server showed the client, and the only way an agent sees the game. Each line has a time `t`, a `type` and `data`, and the KDoc on each `GameEvent` in `core/game/GameEvent.kt` and each `RawRecord` in `core/capture/RawRecord.kt` says what its line carries. An event's text is `plain` plus `spans`, with `§` codes already resolved into the spans. `actionbar`, `death` and `sidebar` were raw records until 2026-11-14, with another shape, so an older capture cannot be cut for them. Why the recorder and the redactor work as they do is in [internals/captures.md](../../../../docs/internals/captures.md).

## When no capture shows it

1. Finish the work that needs no capture.
2. Write a brief, as [Record from a brief](../../../../docs/operations/captures.md#record-from-a-brief) says.
3. End the response with one line the user answers yes or no, such as "Record Golem tiers now? It takes about 3 minutes."
4. On yes, start the client with the brief, and read the capture when the run ends. The live server has real players on it, so the user does all the playing there.

When the user's account cannot reach what the brief needs, [ask players](../../../../docs/operations/captures.md#ask-players-for-a-capture) instead.

## Changing the recorder

- Nothing the recorder does runs before `Recorder.start`. While it is off, each hook is one null check, and that is how it ships in the release jar at no cost.
