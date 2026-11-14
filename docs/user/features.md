# Features

Every feature has a switch in the settings, which `/dsmod` opens.

## HUD

A feature shows a block on the HUD: its name, then rows such as `Eyes: 5/8`. Blocks start in the top-left corner.

To move them, type `/dsmod hud`, or press Edit under HUD in the settings. Drag a block to move it, and scroll over it to resize it. Press Esc to save.

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
