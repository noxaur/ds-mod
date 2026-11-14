# Features

Every feature has a switch in the settings, which `/dsmod` opens.

## HUD

A feature shows a block on the HUD: its name, then rows such as `Eyes: 5/8`. Blocks start in the top-left corner.

To move them, type `/dsmod hud`, or press Edit under HUD in the settings. Drag a block to move it, and scroll over it to resize it. Press Esc to save.

## Chat

### Ability Spam

Hides the message the server sends each time you use an ability that is still on cooldown, such as `This ability is on cooldown for 4.35s`. Holding the button no longer floods your chat.

The first time it hides one, a line in chat says so. Turn off Ability Spam in the settings to see the messages again.

## The End

### Dragon Timer

Shows where the dragon fight stands:

- `Egg: 7s` counts down to the egg's return.
- `Eyes: 5/8` counts the summoning eyes placed.
- `Young Dragon: 1:42` is how long the dragon has been alive.

The timer starts with the next message from the server, so it shows nothing when you arrive in the middle of a fight.

### Golem Status

Shows where the Endstone Protector stands:

- `Stage: Resting` or `Stage: Rising 3/5` is how far it has come, and `Kills Left: 97` counts the zealot kills your lobby still needs. The Protector emerges at zero.
- `Stage: Spawned` with `Alive: 0:08` is how long it has been up.
- `Stage: Defeated` with `Fight: 0:10` shows for a moment after it falls.

It shows nothing outside The End.

### Zealot Tracker

Counts your Summoning Eyes and your other rare drops while you grind in The End:

- `Eyes: 11` is the eyes you have had since you started the game.
- `Eyes per Hour: 66.0` is those eyes divided by the time you have spent in The End, so standing in the hub does not pull it down. It shows `-` for the first minute in The End.
- `All-Time Eyes: 54` is every eye the tracker has seen, across sessions.
- A row such as `[Lvl 1] Enderman: 1 (1 all-time)` appears once you get another rare drop.

It counts only your own drop messages, such as `RARE DROP!`, `EPIC DROP!` and `RNGESUS DROP!`, never what another player types in chat. It does not count kills or show profit. It shows nothing outside The End.

The all-time numbers are saved about once a minute and when you close the game, in `config/dsmod/data/zealot-tracker.json` in your game folder. Delete the file to start again.

## Loot

### Loot Alerts

Shows a drop's name in the middle of the screen and plays a sound when you get a drop of the tier you chose or better. A line in chat says the alert came from Loot Alerts.

Pick the tier with Alert Tier in the settings: Rare, Epic or RNGesus. It starts at Epic, so a Summoning Eye, which is a Rare drop, does not alert you.

It alerts only for your own drop messages, such as `EPIC DROP!`, never for what another player types in chat.

## Slayers

### Slayer Tracker

Counts the slayer bosses you slay:

- `Slain: 3` is the bosses you have slain since you started the game.
- `Spawn to Slain: 0:04` is the average time from a boss spawning to its death. It shows `-` until you slay one that spawned while you were watching.
- `All-Time: 12` is every boss you have slain, across sessions.

A quest you fail counts for nothing. It shows nothing until the first boss spawns, and it counts only the server's own messages, never what another player types in chat.

The all-time number is saved about once a minute and when you close the game, in `config/dsmod/data/slayer-tracker.json` in your game folder. Delete the file to start again.

## Inventory

### Rarity Outlines

Frames an item's slot in the color of its rarity, in your inventory and in every menu:

- White is Common, green Uncommon, blue Rare, dark purple Epic, gold Legendary, light purple Mythic and aqua Divine.
- Red is Special.
- An item with no rarity gets no frame.

The frame sits under the item's tooltip, so it never hides what you are reading.
