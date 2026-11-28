# Game API

How the mod exposes the game to features, starting with the dragon. The words used here are defined in the [glossary](glossary.md).

## Features read the game through one API

A feature reads what the game shows through the API, which folds each source once into typed state and events. Before it, three dragon features each parsed chat with their own patterns and their own idea of the current dragon, and a new one meant writing another parser. The API is built to be complete enough that a feature rarely needs it extended, and it replaces the raw events one area at a time.

## The API is its own source set in explicit API mode

The API compiles on its own, with nothing from Minecraft or the rest of the mod in reach, and Kotlin's explicit API mode makes every public name a deliberate choice. A package inside the pure logic was rejected, because the mode applies to a whole compilation and only a test could keep Minecraft out. Its parts that extend it stay internal, so only the API's developers can add to it.

## The API exposes every fact the game reliably shows

The features built on the API decide what they need, so the API exposes every fact the game reliably shows, named for what it is rather than for a guessed use. Where a dragon died is that place, not a loot spot. Exposing only what today's features read was rejected, because each new feature would then wait on a change to the API. Not needing it yet applies to the API's own machinery, never to the facts it offers.

## A fact is shaped so it is hard to misuse

Before a field is added, the question is whether it makes the API easier or harder to misuse. One that could disagree with another field, such as a current dragon beside the cycle that holds it, is left out. One that saves features work and stays hard to misuse goes in. A live dragon is reached only through the fight that holds it, so nothing can read a dragon after it is gone.

## What the API could not see is said, never left empty

A value the API may not know is either known or unknown, and a feature must handle unknown before it can reach the value. An empty value was rejected, because it hides a detection that failed as if nothing were there. A value that is always known when its event is sent, such as where a dragon died on the tick it died, is plain.

## The world first, then the server's tables, then chat

Each fact is read from the world when the world shows it, checked against the tab list and the boss bar, and named and timed by chat. The server says in chat only when the player places an eye, while the altar's frames show every eye anyone places or takes, and the egg's tower drains and rebuilds block by block. Chat alone was rejected for that reason. Sources that disagree are counted, so a server change shows up rather than being settled quietly.

## State says what is, and events say what just happened

The cycle and each dragon are state, and a feature turned on mid-fight reads them, because events are never replayed. An event carries the facts of its moment and the dragon's identity, and the dragon as it is now is asked for by that identity, which can say it is gone. A live dragon in each event was rejected because it outlives its dragon, and a frozen copy because a feature that kept one would show old health. All state settles before a tick's events are sent.

## Time is the world's game time, counted in ticks

The server times its attacks in ticks, so the API's clock is the client world's game time, which follows the server when it slows. Real time comes from the rate the server actually runs at, measured from its time packets. The rate the client is told was rejected, because dragsim.co can lag without telling it, and a countdown in real seconds would then run fast. Milliseconds were rejected for the API's times, because they drift from the server's ticks.

## A dragon's behaviour is composed from traits

What a kind of dragon does is a list of traits, each reading what the API saw and adding attacks, hazards or debuffs. The Golden Dragon is the common traits plus its own, so adding it changes no feature. Rules that matched only chat lines were rejected, because a debuff may show only in the world or the action bar. An attack the API has no trait for still arrives, named by its callout, when the dragon stops to cast it.

## A listener can be taken back

A listener for the API's events keeps the API watching the world, so it returns a handle that removes it. A listener that could not be removed was rejected, because the debug overlay, which listens only while it is on, would leave the API scanning for the rest of the session.

## The debug overlay reads on the tick

The debug build shows the API's state as text and marks, rebuilt once per tick by the same readout path as a feature, not in the frame. It watches and listens only while on, so being off costs one check a tick.

## The overlay's confidence is measured, not guessed

The debug overlay shows where an arrow meets the dragon with a confidence read from what its own forecast got wrong in the fixture, by movement and ticks ahead. A guess per movement was rejected, because one flight time is exact in a hover and a coin flip in a turn. The forecast stays out of the API, because it was no better than the API's while the dragon flies, and one capture is too little to change what features rely on.

## A dragon's health is its freshest reading

The sidebar, the name tag, the boss bar and the tab list each show the dragon's health, at different rates and precisions: across 28 fights the sidebar's exact value changed every 10 ticks, the name tag's rounded one every 6, the boss bar every 20 and the tab list every 40. So the health is whichever changed last, an exact one first on a tie. Trusting the tab list first was rejected, because it lags the sidebar by up to two seconds. The boss bar is read only once it names the new dragon, since it keeps the previous one's name for up to 21 ticks.

## A dive is told by the dragon's fall, and named by where it lands

The server warns of no dive, so the API calls one when the dragon falls at least 0.8 blocks a tick for three ticks, which no turn does. A dive that lands in or beside a purge's zone is that purge. The gate purge lands on the den's spawn but is named for the gate, since a spawn purge reads as a dive at where the dragon spawned. A distance from the altar's frames was rejected, because it missed dives on the altar's edge.

## Places the server does not name are picked by hand

The tab list and sidebar name an area but not the places inside it, such as the Dragon Altar, so those places are boxes picked in the game with the debug build's Zone Picker and kept as constants in the API. A place found from the world, like the altar's frames, was rejected for these, because most of them have no block the API could find.
