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

Type `/dsmod` in chat to open the settings. ds-mod saves them in `config/dsmod/config.json` in your game folder.

ds-mod runs only while you play on dragsim.co, and stays out of the way in singleplayer and on other servers. To use it anywhere, turn off **Only on DragSim** under General.
