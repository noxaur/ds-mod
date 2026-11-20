# Glossary

The words below mean one thing each, in code, docs and conversation. Use them as defined.

- **Server**: dragsim.co, the Minecraft server ds-mod is for. It is someone else's.
- **Backend**: our own REST service in `backend/`. Never "the server".
- **Area**: a place on the server that a feature can be locked to, told by the tab list's `Area` row or the sidebar's `⏣` place. `Area` in `core/game`.
- **Feature**: one class in `core/feature/<area>/` that holds a `Feature` part, declares its own settings on it, and is placed once in `SettingsTree`, with one switch in the settings. It reads events and returns readouts.
- **Event**: a `GameEvent` the edge posts to `Game`, such as a chat line, a tab list or a slot. Features see events and nothing else from the server.
- **Raw record**: a type of data the recorder writes that no feature sees yet. A feature that needs it promotes it to an event.
- **Readout**: what a feature shows: its HUD block, its slot marks, its world marks, and the notices and alerts it says once.
- **Gate**: a release class, with its nested classes, that sends input for the player or refuses their own, or any mixin that can change Minecraft's code. `InputSitesTest` finds each one in the compiled classes and lists it in `mod/input-sites.txt`.
- **Surface**: a place the mod can put information, such as chat, the HUD or a container overlay. Each has one layer in `edge` that draws it.
- **Role**: what a piece of the mod's text means, such as body, value or muted. The text layer maps a role to a color.
- **Kit**: `Kit` in `edge/ui`, which draws every shape the mod shows, in the player's theme.
- **Theme**: one list of colors the kit draws in.
- **Capture**: a JSONL recording of what the server showed the client, one event or raw record per line.
- **Fixture**: an excerpt of a capture, cut and redacted by `cutFixture`, that a feature's replay test reads. Only fixtures go in git.
- **Transcript**: the expected result beside a fixture, written as a player would describe what the feature did.
- **Brief**: the ordered steps the user or a player plays while a capture records, with a name for the window.
- **Request**: a brief plus requirements, published to the backend by an agent, for players to record.
- **Contribution**: one player's run of a request, redacted and uploaded.
- **Sighting**: how an item looked in a player's own inventory, sent under Report Game Data so the backend can draw items the server's API describes badly.
- **Snapshot**: the containers of one profile lookup, kept by the backend for ten minutes so a tab opened later matches the overview.
