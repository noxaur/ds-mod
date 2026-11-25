<h1>Dragen <img src="assets/icon.svg" height="40" align="center" alt="The Dragen logo: a raised dragon's head in gold, white and blue"></h1>

[![Discord](https://img.shields.io/badge/Discord-join-5865F2?logo=discord&logoColor=white)](https://discord.gg/YUZCYAnr7F)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-support-FF5E5B?logo=kofi&logoColor=white)](https://ko-fi.com/noxaur)
[![GitHub stars](https://img.shields.io/github/stars/noxaur/ds-mod?style=flat&logo=github)](https://github.com/noxaur/ds-mod/stargazers)

Dragen is a mod for [DragSim](https://dragsim.co/) (Dragon Simulator) that makes the game a lot more fun without changing too much. It should feel like part of the game.
<sub>Dragen is the Norwegian word for "the dragon".</sub>

If you like Dragen, [star the repo](https://github.com/noxaur/ds-mod) so more players find it.

## Installation guide

You need:

- Minecraft 26.2 with [Fabric Loader](https://fabricmc.net/use/installer/) 0.20.1 or later, on Java 25 or later.
- [Fabric API](https://modrinth.com/mod/fabric-api) and [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin).

Download `dragen-<version>.jar` from [Releases](https://github.com/noxaur/ds-mod/releases) and drop it in your `mods` folder with the two mods above. Skip the `-debug` jar. Join dragsim.co and type `/dragen` to open the settings. `/dg` and `/dragon` work too. The [install guide](docs/user/install.md) covers the details.

## Features

Every feature is explained in the [feature guide](docs/user/features.md).

<details>
<summary><h3>General</h3></summary>

- **DragSim Only** – Runs Dragen only on dragsim.co, until you turn it off.
- **Ctrl Click** – On a Mac, makes Ctrl + left click a Ctrl click instead of a right click.
- **Auto Click** – Left or right clicks 9.9 times a second while you hold or toggle a key.

</details>

<details>
<summary><h3>Camera & Visuals</h3></summary>

- **Fullbright** – Lights caves and nights as brightly as day.
- **Hide Explosions** – Hides explosion particles.
- **Animations** – Swing like 1.7, Exhibition, Spin or Stab, and move or resize your held item.
- **Player Model** – Resize, tilt, spin and pose your player or everyone's, with a live preview in the settings.
- **Third Person** – Set the camera's distance, height and tilt, and which views F5 goes through.
- **Free Look** – Hold Left Alt to look around in third person without turning.
- **Etherwarp Outline** – Outlines the block your Etherwarp will land on, or shows why it would fail.

</details>

<details>
<summary><h3>HUD</h3></summary>

- **Ability Cooldowns** – Counts down every item ability, in a list and on the item's slot.
- **Equipment Display** – Shows your armor and pet as icons, on the HUD or beside your hotbar.
- **Performance Stats** – Shows your FPS, ping and the server's TPS, and turns them a warning color when they lag.
- **HUD Background** – Puts a dark background behind the HUD, so you can read it over bright blocks.
- **Hide In F3** – Hides the HUD while the F3 debug screen is open.

</details>

<details>
<summary><h3>Chat & Commands</h3></summary>

- **Command Binds** – Sends a command with a key, a combo or a sequence. Group, fold and share them.
- **Profile Viewer** – Type `/pv` and a name to see a player's skills, slayers, dungeons, pets and items.
- **Item Overview** – Type `/dragen items` to search every item you own, from every container, in one screen.
- **Waypoints** – Save, style and group waypoints, share them in any chat, and click shared coordinates to see them.
- **Copy Chat** – Right-click a message to copy it as text, format codes or JSON.
- **Hide Cooldown Messages** – Hides the chat message that says an ability is on cooldown.

</details>

<details>
<summary><h3>Inventory & Menus</h3></summary>

- **Item Rarity** – Colors each slot by its item's rarity, as a Frame, an Aura or an Underglow, with optional sparkles.
- **Slot Lock** – Locks slots and items so a misclick can't move or drop them.
- **Armor Swap Guard** – Stops a right click from swapping the armor you wear.
- **Better UI** – Turns every button and tab Dragen adds to menus on or off at once.
- **Menu Tabs** – Adds an item search and your own command tabs to every menu.
- **SkyBlock Menu Click** – Opens the SkyBlock Menu when you click its item, instead of picking the item up.
- **HOTD Button** – Opens the Heart of the Dragon from the Dragon Sim Menu.
- **Storage Overview** – Hover a Storage page to see its items, or see every page and the Ender Chest at once.
- **Forge Craft** – Repeats your last forge craft with a key, one click per press.
- **HOTD Planner** – Marks what to click next in the Heart of the Dragon for the block you mine.
- **Wardrobe Keys** – Press 1 to 9 in the Wardrobe to click that slot.
- **Pets Menu** – Outlines your active pet in the Pets menu.
- **Profile Cooldown** – Shows in Profile Management when you can switch profiles again.
- **Keep Cursor** – Keeps the cursor where it is when one menu opens another.

</details>

<details>
<summary><h3>The End</h3></summary>

- **Zealot Tracker** – Counts your Summoning Eyes, eyes per hour and kills per eye, for the session and all-time.
- **Dragon Timer** – Shows the egg countdown, the eyes placed and how long the dragon has been alive.
- **Fireball Alert** – Warns you just before a dragon throws fireballs.
- **Protector Status** – Shows the Endstone Protector's stage and the kills left until it emerges.
- **Protector Spawn Beam** – Shows a beam where the next Protector will emerge, as soon as the last one dies.
- **Boss Fight Tracker** – Shows your rank, damage and loot from every dragon and Protector fight.
- **Eye Guard** – Stops a quick second click from taking back the Summoning Eye you just placed.

</details>

<details>
<summary><h3>Combat</h3></summary>

- **Damage Numbers** – Shortens damage numbers to 74.5M, shows only crits, or hides them.
- **Boss Beacon** – Shows a beam over each boss you pick, with its name and distance.
- **Boss Tracer** – Draws a line from your crosshair to each boss you pick, so you can find it when you look away.

</details>

<details>
<summary><h3>Slayers & Loot</h3></summary>

- **Slayer Tracker** – Shows your quest progress, the XP to your next level and your average kill time.
- **Slayer Boss Alert** – Shows a title and plays a sound when your slayer boss spawns.
- **Rare Drop Alert** – Shows a title and plays a sound for drops of the tier you pick or higher.

</details>

<details>
<summary><h3>Contribute</h3></summary>

- **Report Game Data** – Sends game data Dragen hasn't seen yet to its developers, with player names removed. It's off until you turn it on.

</details>

## Credits

- [DragsimUtils](https://github.com/RealZones/DragsimUtils)
- [SkyHanni](https://github.com/SkyHanniStudios/SkyHanni)
- [SkyOcean](https://github.com/meowdding/SkyOcean)
