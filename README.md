<img src="assets/icon.svg" width="96" alt="The ds-mod logo: a raised dragon's head in gold, white and blue">

# ds-mod (beta)

A client-side mod for [Dragon Simulator](https://dragsim.co). Open source, built on Fabric.

I want to bring the popular features from Hypixel SkyBlock mods to Dragon Simulator. Suggestions and contributions are welcome in the [Discord](https://discord.gg/YUZCYAnr7F) or as a [GitHub issue](https://github.com/noxaur/ds-mod/issues). If you can't contribute code, you can support the mod on [Ko-fi](https://ko-fi.com/noxaur) or tell others about it.

## Features

### General
- **DragSim Only** – Runs ds-mod only on dragsim.co, until you turn it off.
- **Ctrl Click** – Keeps Ctrl and a left click a Ctrl click on a Mac.
- **Auto Click** – Left or right clicks 9.9 times a second while you hold or toggle a key and walk.

### Camera & Visuals
- **Fullbright** – Lights caves and nights as brightly as day.
- **Hide Explosions** – Hides the clouds that explosions throw up.
- **Animations** – Restyle your swing as 1.7, Exhibition, Spin or Stab, and move or resize your held item.
- **Player Model** – Resize, tilt, spin and pose your player, or everyone's, with a live preview in the settings.
- **Camera** – Set the third-person distance, height and tilt, and which views F5 cycles through.
- **Free Look** – Hold Left Alt to look around in third person without turning.
- **Etherwarp Outline** – Boxes the block your Etherwarp will land on, or says why it would fail.

### HUD
- **Ability Cooldowns** – Times every item ability, in a list and on the item's slot.
- **Active Pet** – Shows your pet with its held item, its XP, and how long until it levels up.
- **Equipment Display** – Shows your armor and pet as icons, on the HUD or beside your hotbar.
- **Inventory HUD** – Shows your inventory as a grid on the HUD.
- **Performance Stats** – Shows your FPS, your ping and the server's TPS, with a warning color when they lag.
- **HUD Background** – A dark plate behind the HUD, so it reads over a bright world.
- **Hide In F3** – Hides the HUD while the F3 debug screen is open.
- **Chat HUD Editor** – Move any HUD block with the chat open, or open the full editor with `/dsmod hud`.

### Chat & Commands
- **Command Binds** – Send a command with a key, a combo or a sequence. Group, fold and share them.
- **Profile Viewer** – Type `/pv` and a name to see their skills, slayers, dungeons, pets and items.
- **Item Overview** – Type `/dsmod items` to search every item you own, from every container, in one screen.
- **Waypoints** – Save, style and group waypoints, share them in any chat, and click shared coordinates to see them.
- **Copy Chat** – Select messages and copy them as text, format codes or JSON.
- **Hide Cooldown Messages** – Hides the "ability is on cooldown" messages.

### Inventory & Menus
- **Item Rarity** – Colors each slot by its item's rarity, as a Frame, an Aura or an Underglow, with sparkles if you like.
- **Slot Lock** – Lock slots and items so a misclick can't move or drop them.
- **Armor Swap Guard** – Stops a right click from swapping the armor you wear.
- **Better UI** – Turns the controls the mod adds to menus on or off at once.
- **Menu Tabs** – Your own command tabs and an item search in every menu.
- **Back Buttons** – Adds Go Back to menus that have none.
- **SkyBlock Menu Click** – Clicking the SkyBlock Menu item opens the menu instead of picking it up.
- **HOTD Button** – Opens the Heart of the Dragon from the Dragon Sim Menu.
- **Forge Craft** – Repeat your last forge craft with a key, one click per press.
- **Wardrobe Keys** – Press 1 to 9 in the Wardrobe to click that slot, once the server lets you.
- **Menu Theme** – Dark or glass menus, inventory and tooltips.
- **Fixed Menu Layout** – Go Back, Close and your inventory sit in the same place in every menu.
- **Hide Glass Panes** – Hides the blank glass panes that pad out menus.
- **Keep Cursor** – The cursor stays put when one menu opens another.

### The End
- **Zealot Tracker** – Eyes, eyes per hour and kills per eye, for the session and all-time.
- **Dragon Timer** – The egg countdown, the eyes placed and how long the dragon has been alive.
- **Fireball Alert** – Warns you before a dragon throws fireballs.
- **Protector Status** – The Endstone Protector's stage and the kills left until it emerges.
- **Protector Spawn Beam** – A beam where the next Protector will emerge, as soon as the last one dies.
- **Boss Fight Tracker** – Your placement, damage and loot from every dragon and Protector fight.
- **Eye Guard** – Stops a second click from taking back the eye you just placed.

### Combat
- **Damage Numbers** – Shortens damage numbers to 74.5M, shows only crits, or hides them.
- **Boss Beacon** – A beam over each boss you pick, with its name and distance.
- **Boss Tracer** – A line from your crosshair to each boss you pick, to follow when you look away.

### Slayers & Loot
- **Slayer Tracker** – Quest progress, the XP to your next level and your average kill time.
- **Slayer Boss Alert** – A title and a sound when your slayer boss spawns.
- **Rare Drop Alert** – A title and a sound for drops at or above the rarity you set.

### Contribute
- **Report Game Data** – Sends game data new to ds-mod to its developers, names removed. Off until you turn it on.

Type `/dsmod` to access the settings menu. [feature guide](docs/user/features.md).

## Install

Download the jar from [Releases](https://github.com/noxaur/ds-mod/releases) and put it in your `mods` folder with [Fabric API](https://modrinth.com/mod/fabric-api) and [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin). The [install guide](docs/user/install.md) lists the versions.

## Contributing

Suggest features in the [Discord](https://discord.gg/YUZCYAnr7F) and report bugs in [issues](https://github.com/noxaur/ds-mod/issues). I haven't reached the late game, so recordings of it help the most.

Reviewing a pull request costs me more time than writing one costs you. Before you open one:

- Read [CONTRIBUTING.md](CONTRIBUTING.md) and run the checks.
- Test the feature in game yourself, and say what you saw.
- I recommend using the most capable model you have access to, and reviewing its work with a capable model from another lab (e.g. Opus 5.5 writes the code, Sol 6.1 verifies it).

A pull request that skips those takes longer to review than the fix would take me to write.

Made by [noxaur](https://github.com/noxaur). Not affiliated with the Dragon Simulator team. Licensed under [Apache 2.0](LICENSE).
