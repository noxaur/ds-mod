# Features

Every feature has a switch in the settings, which `/dsmod` opens.

## HUD

A feature shows a block on the HUD: its name, then rows such as `Eyes: 5/8`. Blocks start in the top-left corner, on a dark plate.

To arrange them, type `/dsmod hud`, or press Edit under Interface in the settings.

- Drag a block to move it. Near the screen's edge, its center or another block, it lines up with it. Hold Shift to place it freely.
- Scroll over a block to resize it. You can also click it and press the plus and minus beside Scale. Reset sends it back to the corner.
- HUD Scale resizes every block at once.
- Background turns the plate behind the blocks on or off.
- Press Done or Esc to save.

HUD Scale and HUD Background are also under Interface in the settings. The HUD grows with Minecraft's own GUI Scale as well, like the rest of the game.

## Theme

ds-mod's own screens come in two looks. Charcoal is dark and is the default. Vanilla matches Minecraft's light gray menus. Pick one with Theme under Interface in the settings.

## Chat

### Ability Spam

Hides the message the server sends each time you use an ability that is still on cooldown, such as `This ability is on cooldown for 4.35s`. Holding the button no longer floods your chat.

The first time it hides one, a line in chat says so. Turn off Ability Spam in the settings to see the messages again.

### Copy Chat

Right-click a message in the open chat to copy it. The whole message is copied as plain text, also when it runs over several lines, and it flashes with `Copied` beside your mouse.

It is on from the start. Turn off Copy Chat under Chat in the settings to stop it.

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

### Boss Beacon

Shows a beam where the Endstone Protector will emerge, labeled `Protector` with the distance to it. The spot is known from the moment the last Protector falls, so you can stand there before the next one is up.

The beam starts out showing for the last 25 zealot kills before the Protector emerges, and goes away when it does. To change that, press Edit beside Beam Timing under The End in the settings.

The bar is one Protector cycle: the 200 zealots your lobby kills between two Protectors, split into the four tiers it rises through. The beam shows while the cycle is inside a blue section.

- Press on an empty part of the bar and drag to draw a section. A click without dragging adds a short one.
- Drag a section's end to make it longer or shorter, or drag its middle to slide it.
- Sections that meet become one. Drag back before you let go and they come apart again.
- Right-click a section to remove it.
- The line under the bar reads the section you last touched, in zealots killed and as a share of the cycle, such as `100 to 130 of 200 kills (50% to 65%)`.
- During the Fight keeps the beam on the spot after the Protector has emerged, and Reset goes back to the last 25 kills.

With the keyboard, the arrow keys slide the section you last touched, by ten with Shift, and Delete removes it. Done takes you back to the settings.

The beam also shows when you arrive in The End partway through a cycle. It shows nothing outside The End.

### Boss Drops

Shows how your last fight against a dragon or the Endstone Protector went:

- `Wise Dragon: #3 with 2,376,120` is your place among the damagers and the damage you did.
- `Loot: 13x Wise Dragon Fragment` is what the dragon gave you. It arrives a few seconds after the dragon falls.
- `Fights: 4 (12 all-time on this boss)` counts your fights since you started the game, and every fight against this boss across sessions.

A fight counts only when the server's summary has your own `Your Damage` line, so a boss you did not hit counts for nothing. It shows nothing until your first fight, and nothing outside The End.

The all-time numbers are saved about once a minute and when you close the game, in `config/dsmod/data/boss-drops.json` in your game folder. Delete the file to start again.

### Protect Placed Eyes

Ignores a second click on the altar frame you just clicked with a Summoning Eye or a Golden Eye. On the server that second click takes your eye back out, without a word in chat.

The frame stays protected for as long as you keep clicking it or hold the button, and for a little under a second after. Wait a second and click again to take the eye back on purpose. Clicks on any other block are never held back. Turn off Protect Placed Eyes in the settings to click freely.

### Zealot Tracker

Counts your Summoning Eyes and your other rare drops while you grind in The End:

- `Eyes: 11` is the eyes you have had since you started the game.
- `Eyes per Hour: 66.0` is those eyes divided by the time you have spent in The End, so standing in the hub does not pull it down. It shows `-` for the first minute in The End.
- `All-Time Eyes: 54` is every eye the tracker has seen, across sessions.
- `Kills: 164 (2,310 all-time)` estimates the zealots you have killed, and `Kills per Eye: 14.9` divides them by your eyes. Both appear after your first kill.
- A row such as `[Lvl 1] Enderman: 1 (1 all-time)` appears once you get another rare drop.

It counts only your own drop messages, such as `RARE DROP!`, `EPIC DROP!` and `RNGESUS DROP!`, and only in The End, never what another player types in chat. The server never says who killed a zealot, so a kill is one that died at the same moment you gained Combat XP. In a crowd it can be a few kills off. Zealot Bruisers are not counted, and it does not show profit. It shows nothing outside The End.

The all-time numbers are saved about once a minute and when you close the game, in `config/dsmod/data/zealot-tracker.json` in your game folder. Delete the file to start again.

## Loot

### Loot Alerts

Shows a drop's name in the middle of the screen and plays a sound when you get a drop of the tier you chose or better. A line in chat says the alert came from Loot Alerts.

Pick the tier with Alert Tier in the settings: Rare, Epic or RNGesus. It starts at Epic, so a Summoning Eye, which is a Rare drop, does not alert you.

It alerts only for your own drop messages, such as `EPIC DROP!`, never for what another player types in chat.

## Slayers

### Slayer Tracker

Counts the slayer bosses you slay:

- `Quest: 4,000 XP of Zombies` is what your quest asks for, and it turns to `Quest: Boss` while your boss is alive.
- `Zombie Slayer 5: 13,810 XP to go` is your slayer level and what the next one needs. It appears when you complete a quest.
- `Slain: 3` is the bosses you have slain since you started the game.
- `Spawn to Slain: 0:04` is the average time from a boss spawning to its death. It shows `-` until you slay one that spawned while you were watching.
- `All-Time: 12` is every boss you have slain, across sessions.

A quest you fail counts for nothing. It shows nothing until your first quest or boss, and it counts only the server's own messages, never what another player types in chat.

The all-time number is saved about once a minute and when you close the game, in `config/dsmod/data/slayer-tracker.json` in your game folder. Delete the file to start again.

## General

### Fireball Alert

Shows `Fireball` in the middle of the screen and plays a sound when a dragon says it is about to throw fireballs. It works only in The End, and its switch is under The End in the settings.

### Slayer Boss Alert

Shows `Boss Spawned` in the middle of the screen and plays a sound when your own slayer boss spawns. Its switch is under Slayers in the settings.

### Explosion Particle Hider

Hides the clouds that explosions throw up, so a crowded boss fight stays visible. It is off until you turn it on.

### Fullbright

Lights the whole world, so a cave or the night is as bright as day. It is off until you turn it on.

## Inventory

### Armor Swap Guard

Stops a right click from swapping the armor in your hand onto you. A click that misses the altar no longer puts the piece on and takes your own off.

Sneak and right click to swap on purpose. A right click on a block still works as before. It is off until you turn it on.

### Rarity Outlines

Frames an item's slot in the color of its rarity, in your inventory, in every menu and on your hotbar:

- White is Common, green Uncommon, blue Rare, dark purple Epic, gold Legendary, light purple Mythic and aqua Divine.
- Red is Special.
- An item with no rarity gets no frame.

The frame sits under the item's tooltip, so it never hides what you are reading. Turn off Hotbar Outlines in the settings to keep the frames off the hotbar.
