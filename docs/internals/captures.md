# Captures

How the mod records what the server showed, and how a recording becomes a test that holds no one's name. Recording one is in [operations/captures.md](../operations/captures.md).

## A capture is a recording of what the features saw

The capture records events as they were posted, and a test replays them through the same game logic the player runs, so nothing stands between a capture and a test that could drift from the real input. A test that built its own events was rejected, because it would test the author's idea of a closed server.

## A data type is an event or a raw record, never both

Nobody can design an event for data they have not seen, so the recorder also writes everything else the server sent, as raw records, and the first capture of a screen shows what its event must carry. A feature that needs that data promotes it to an event. Two producers for one type were rejected, because a test could then pass on data the player's game never builds.

## A fixture is an excerpt with a transcript

Only an excerpt of a capture goes in git, as a feature's fixture, with player names and their chat replaced. The transcript beside it is the expected result, written as a player would describe it, so a reviewer who knows the game can judge it without reading the code.

## A line carries the client tick that posted it

A line's time can't tell ticks apart: two events 49 ms apart can be one tick, and two 1 ms apart can be two. Grouping a replay by time therefore merges and splits ticks, and a transcript shows a HUD the player never had. So each line carries the tick that posted it, and the replay ticks once after that tick's events.

## The recorder ships and runs only for a contribution

Developers' accounts can't reach all of the server, so players record it for them, and the recorder ships in the jar a player installs. It runs only for a contribution the player accepted, or in the debug build. While none runs it costs a frame one check, and the file is redacted before it leaves the machine. Recording reads game state on the client thread, so writing, redacting and uploading happen on other threads.

## A capture is redacted by rules that say where a name sits, and typed chat fails closed

A fixture goes into a public repository, so a capture is rewritten to hold no player name and no typed chat. A missed name leaves the machine, and a word taken for a name changes what the server sent, so each rule says where in a text a name sits. Typed chat fails closed: whatever follows the first colon of a line is typed, unless the line is indented or spoken by one of the server's own characters, as its lines with a colon almost always are. Needing a known name was rejected, because a speaker no other line names kept both name and words.

## Fixtures are checked for names two ways

Raw captures never reach git, so a check against the names in local captures guards only the machine that cut the fixture. So the fixture is also read as if it were a capture, and a name in any place a rule knows holds one must be an alias. That check runs in CI too, and catches a fixture edited by hand.
