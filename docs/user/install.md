# Install ds-mod

ds-mod is a Fabric mod for the Dragon Simulator server, dragsim.co. It runs only on your client, and it's ready with no setup.

## What you need

- Minecraft 26.2 with Fabric Loader 0.20.1 or later.
- Java 25 or later.
- [Fabric API](https://modrinth.com/mod/fabric-api) and [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin).

## Install

1. Put `dsmod-<version>.jar`, Fabric API and Fabric Language Kotlin in the `mods` folder of your game folder.
2. Start Minecraft with the Fabric profile.
3. Join dragsim.co.

Use the jar without `-debug` in its name. The debug jar is for developers, and you don't need it.

## Settings

Type `/dsmod` or `/dsm` in chat to open the settings. Add words to search them, as in `/dsmod zealot`. The settings open where you left them, and ds-mod saves them in `config/dsmod/config.json` in your game folder when you close them.

To open them with a key, pick one in **Settings Key** under General. It has no key at first, because DragSim Utils opens on Right Shift and one press would open both.

Click the star beside a setting's name to keep it in **Favorites**, the first category. Favorites lists your starred settings in the order the categories do, and starts with Loot Alerts, Zealot Tracker, Slayer Tracker and Slayer Boss Alert. Click a star again to take it out. A search looks through every category, so Favorites hides while you search.

ds-mod runs only while you play on dragsim.co, and stays out of the way in singleplayer and on other servers. To use it anywhere, turn off **DragSim Only** under General.
