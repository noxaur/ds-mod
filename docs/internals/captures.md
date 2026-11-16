# Captures

How the mod records what the server showed, and how a recording becomes a test that holds no one's name. Each capture type's line is in the KDoc on its `GameEvent` or `RawRecord`, and recording one is in [operations/captures.md](../operations/captures.md).

## A capture is a recording of what the features saw

The edge turns what the server sends into a `GameEvent`, and the capture records the events as they were posted. A test replays the recording through the same `Game` the player runs, so nothing stands between a capture and a test that could drift from the real input.

A type of data is either an event or a raw record, and never both. Raw records exist because nobody can design an event for data they have not seen: the recorder writes everything else the server sent, with full fidelity, so the first capture of a screen shows what its event must carry. A feature that needs that data promotes it to an event. Two producers for one type would let a test pass on data the player's game never builds, so raw types are a closed list, `RawRecord`, and `CaptureWriterTest` fails on one that shares an event's type.

Only an excerpt of a capture goes in git, as a feature's fixture, with the player names and their chat replaced. The transcript beside it is the expected result, written as a player would describe it, so a reviewer who knows the game can judge it without reading the code.

## A line carries the client tick that posted it

The client posts events as they arrive and calls `Game.tick` once, from `END_CLIENT_TICK`. A capture's `t` is `System.currentTimeMillis()` at the post, so it is not a tick: two events 49ms apart can be one tick or two, and two events 1ms apart can be two ticks when the boundary falls between them. Grouping a replay by `t / 50` therefore merges ticks and splits them, and the transcript shows a HUD the player never had, or hides one they did.

The writer keeps a counter, stamps it on the line as `tick`, and advances it by one when that client tick ends. The advance is a single increment on the client thread, read into the record before the line is queued, so an empty tick adds no line and the cost of a tick does not grow with how much the tick recorded. A line without `tick` is a capture from before this. Replaying one ticks after every event, because that is how its transcript was written, and a guessed boundary would change it. A line that has `tick` is posted with the other events of that tick, and the replay ticks once after them.

`cutFixture` copies `tick` onto each event it keeps. The tab list, sidebar and container it writes for the state at the window's start have none: those were posted on earlier ticks the window does not keep. Redaction copies `tick` too, since it rebuilds an event line.

## The recorder is in the release jar, and runs only for a contribution

Developers' accounts cannot reach all of the server's content, so players record it for them. That needs the recorder in the jar a player installs, though it reads private client fields and records everything, chat included. Three things make that safe:

- It starts only from a contribution the player accepted, or from the debug build's `/dscapture` and `-Pbrief`. While none runs, every hook is one null check, and the recorder costs a frame nothing.
- The file never leaves the machine as recorded. The mod redacts it first.
- The recorder only reads what the server already sent the client.

Recording is client-thread work that no thread can do for it, because it reads game state. Writing, redacting, compressing and uploading happen on other threads, and the writer opens the file on its own thread, so starting a recording touches no disk on the client thread.

A recording reads every item and name in view four times a second, and Minecraft's codec was most of what that cost: over a millisecond a pass with a full inventory and a crowd of armored mobs. Almost none of them change between passes, so the session encodes a value only when it differs from the copy it kept, and `PerfTest` caps both passes.

## A capture is redacted by rules that say where a name sits

A capture holds player names and private chat, and a fixture cut from it goes into a public repository. `Redactor` rewrites a capture so it holds neither. `PlayerNames` is the one place that knows where the server puts a name, so the names `cutFixture` replaces everywhere are the names `FixtureLeakTest` looks for. Both live in `core` and not the debug build, so that a release build can redact a contribution as well.

A redactor fails in two directions. A name it misses leaves the machine, and a word it takes for a name changes what the server sent, so a fixture replays a line the server never wrote. Each rule therefore says where in a text a name sits, and a name is always replaced there. A name a rule found is also replaced wherever else it stands, because names turn up where no rule looks, such as a head's item name, unless that would rewrite the server's own words.

Two kinds of name are kept to the places a rule found them:

- A plain name on a player entity may be an NPC's, since the server's NPCs are player entities, some with plain names such as `Dungeons`.
- A player can be named what the server calls itself or a place, as a staff account is named after the server. A name that the sidebar's title, its `⏣` place, the tab list's area row or its header also shows is not replaced across the capture. A player named after a game word the server writes only in other places, such as `Eye`, still has it replaced everywhere.

What a player typed is found by shape, not by a list of formats. The server's whisper, party and guild lines are in no capture, so a list would be a guess that passes its own tests. The shape is a known name followed by a `: ` on the same line, which the server never writes in its own lines; across every capture so far, no server line lost text to it. It costs the text of a server line that names a player and then has a `: `, and of a message that starts like a speaker's, `Word: `, without a tag such as `[NPC]`. A message from a player no line of the capture names keeps both the name and the text.

Typed text is found only in a `system` event, so a raw `chat` record, whose event carries the same line, is dropped, and so is an older capture's `system` line that does not read as today's event. A component that spells a name across several pieces, one per color, is replaced by its redacted plain text, because no single string holds the name to replace, and an older raw text that names someone loses its `§` codes for the same reason.

## FixtureLeakTest checks names two ways, because CI has no captures

`FixtureLeakTest` looks for the names `PlayerNames` learns from the captures in `mod/run/dsmod/captures/`, wherever they stand in a fixture. Raw captures never reach git, so that check guards only the machine that cut the fixture. So the test also reads each fixture as if it were a capture, and fails on any name in a place `PlayerNames` knows holds one that is not a `PlayerN` alias. That check runs everywhere, CI included, and catches a fixture written or edited by hand. Neither sees typed text, or a name the server writes in a line no rule knows, which is why a fixture is still read before it is kept.
