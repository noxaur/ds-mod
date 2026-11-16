# ds-mod docs

## Using ds-mod

- [Install ds-mod](user/install.md): what the mod needs, where the jar goes, and how to open settings.
- [Features](user/features.md): what each feature shows, and how to move a readout.

## Working on ds-mod

Start with the [contribution guide](../CONTRIBUTING.md) and the [development runbook](operations/development.md).

Internal docs record the decisions, the constraints and the traps that the code alone can't explain, one page per area. Most code changes need none. Follow the [documentation rules](../AGENTS.md#docs) before adding one.

- [Architecture overview](internals/overview.md): the three parts, why `core` is pure, why the mod runs only on dragsim.co, and how the jars are built.
- [Glossary](internals/glossary.md): the words the code and the docs use, each with one meaning.
- [Features](internals/features.md): how a feature gets events and shared state, and why it never runs in a frame.
- [Captures](internals/captures.md): what the recorder writes, why it ships in the release jar, and how a capture is redacted.
- [Contributions](internals/contributions.md): requests, uploads, item sightings, and the one switch for everything the mod reports.
- [Backend and website](internals/backend.md): the Effect API inside Void, the one file that touches the database, and the website on Vercel.
- [Sign-in](internals/sign-in.md): why a player is their Minecraft account, and how the mod and the website sign in.
- [Profiles](internals/profiles.md): how the backend looks players up and rebuilds item tooltips, and how the viewer draws them.
- [UI](internals/ui.md): text roles, the layers, the kit and its themes, the HUD and world marks.
- [Settings](internals/settings.md): how favorites and the settings key are built on MoulConfig's screen.
- [Menus](internals/menus.md): how the server's menus are redrawn, and the controls Better UI adds to them.
- [Input and the camera](internals/input.md): Free Look, Animations, Command Binds and Slot Lock, where each meets Minecraft's input, and how each place the mod acts for the player gets approved.
- [Testing and CI](internals/testing.md): replay tests, the perf caps, game tests behind the developer, and CI's time limit.

### Runbooks

- [Development](operations/development.md): set up a checkout, run each part, change the backend's tables and run the checks.
- [Captures](operations/captures.md): record a capture, from a brief or from players, and cut a fixture from it.
- [CI and releases](operations/release.md): what CI runs, and how and when a beta ships.
- [Deployment](operations/deployment.md): where the backend and the website run, how to deploy them, and where their secrets live.
