# Features

`/dsmod` opens the settings. Everything is on unless marked off.

<sub>Trackers read only the server's own messages, never player chat. All-time numbers are saved in `config/dsmod/data/`. Delete a file to reset it.</sub>

## HUD

`/dsmod hud` opens the editor.

<sub>Scroll over a block to resize it. Hold Shift to place it without snapping.</sub>

### Copy Chat

Select messages in the open chat with the right mouse button, then pick how to copy them.

Selecting works as it does for files:

| Do this | To |
| --- | --- |
| Right-click a message | Select it |
| Hold the right button and drag | Select every message you pass. Hold it past the top or bottom of the chat to scroll |
| Shift and right-click | Select from the message you clicked last to this one |
| Ctrl and right-click | Add a message, or take a selected one out. With a drag, a whole range |
| Ctrl+A, with the menu open | Select the whole chat |
| Shift and Up or Down, with the menu open | Grow or shrink the selection by one |

On macOS, Cmd takes the place of Ctrl.

When you let go, a small menu opens just right of your mouse. It leaves the messages above and below your mouse free, so if you missed, right-click the one you meant.

- **Text** copies the messages as plain text, one per line. Ctrl+C does the same without the mouse.
- **Formatted** keeps the colors and styles as `&` codes, such as `&6&lRARE DROP!`. A color with no code of its own is written as `&#12ab34`.
- **JSON** copies each message as Minecraft's own text component, for tools and bug reports. Several messages come as a list.

Left and Right move between the three and Enter takes one. Escape or a click anywhere else puts the menu and the selection away.

A message is copied whole, also when it wraps over several rows. A block the server sends, such as the list of active boosters, is selected a line at a time, so drag over the lines you want. The spaces that center such a line are left out, and so is an empty line at the start or end of what you selected. What you copied flashes with `Copied` beside your mouse.

It is on from the start. Turn off Copy Chat under Chat in the settings to stop it.

## The End

### Dragon Timer

```
Egg: 7s
Eyes: 5/8
Young Dragon: 1:42
```

<sub>Blank if you arrive in the middle of a dragon.</sub>

### Golem Status

```
Stage: Rising 3/5
Kills Left: 97
```

### Boss Beacon

A beam where the next Endstone Protector will emerge, from the moment the last one falls.

It shows for the last 25 kills of the 200-kill cycle. Edit beside Beam Timing opens a bar of the cycle, and the beam shows inside the blue sections.

<sub>Drag to draw, move or resize a section. Right-click removes one. During the Fight keeps the beam on after the Protector emerges.</sub>

### Boss Drops

```
Wise Dragon: #3 with 2,376,120
Loot: 13x Wise Dragon Fragment
Fights: 4 (12 all-time on this boss)
```

<sub>Counts only fights where you damaged the boss.</sub>

### Zealot Tracker

```
Eyes: 11
Eyes per Hour: 66.0
All-Time Eyes: 54
Kills: 164 (2,310 all-time)
Kills per Eye: 14.9
[Lvl 1] Enderman: 1 (1 all-time)
```

<sub>Eyes per Hour counts only time in The End. Kills are an estimate from your Combat XP, and can be a few off in a crowd. Zealot Bruisers are not counted.</sub>

### Protect Placed Eyes

Ignores a second click on the altar frame you just put an eye in.

<sub>Wait a second and click again to take the eye back.</sub>

### Fireball Alert

## Slayers

### Slayer Tracker

```
Quest: 4,000 XP of Zombies
Zombie Slayer 5: 13,810 XP to go
Slain: 3
Spawn to Slain: 0:04
All-Time: 12
```

<sub>A failed quest is not counted.</sub>

### Slayer Boss Alert

## Loot

### Loot Alerts

<sub>Alert Tier is Rare, Epic or RNGesus. It starts at Epic, so a Summoning Eye does not alert.</sub>

## Inventory

### Rarity Outlines

<sub>Hotbar Outlines turns the hotbar frames off.</sub>

### Armor Swap Guard

<sub>Off by default. Sneak and right click to swap on purpose.</sub>

### Slot Lock

Locks slots and items of your inventory, so a misclick cannot move or drop them.

| Do this, in any menu | To |
| --- | --- |
| Press L over a slot of your inventory | Lock the slot, or unlock it. Whatever you put there is kept |
| Press K over an item in your inventory | Lock the item, or unlock it. The lock follows the item from slot to slot |
| Hold L over a hotbar slot, move to a slot in the rows above, and let go | Link the two, which also locks both. A line joins them. The same again ends the link |
| Shift-click either slot of a link | Trade the two items' places |

A padlock marks each one: yellow for a slot, blue for a linked slot and green for an item.

- **What a lock stops:** clicks, number keys, the drop key and a double click that would gather from it. The drop key also keeps the item when no menu is open.
- **Shift-clicks elsewhere:** an item that would land in a locked slot goes to a free slot that is not locked, or stays put when there is none.
- **Which items:** only an item that does not stack, such as a weapon or armor, can be locked by itself. For a stack, lock its slot.
- **Armor and the off hand** can be locked too.

Slot Lock Key and Item Lock Key under Inventory in the settings take any key or mouse button. The server can still move a locked item itself, as when you sell it.

## Chat

### Ability Spam

<sub>Hides `This ability is on cooldown for 4.35s`.</sub>

## General

### Camera

Shapes the third-person view you get with F5. Each setting starts at the game's own, so nothing changes until you move one.

| Setting | Does |
| --- | --- |
| Camera Distance | Puts the camera 1 to 50 blocks from you. The game's own is 4 |
| Camera Height | Raises the camera up to 3 blocks, or lowers it up to 2. It stops under a ceiling |
| Camera Tilt | Angles the camera down up to 45 degrees, or up. Your aim stays where it is |
| F5 Views | Makes F5 skip the front view or the back view |
| Camera Smoothing | Slides the camera out when you leave first person |

A wall behind you still pulls the camera in, as it does in the game.

### Explosion Particle Hider

<sub>Off by default.</sub>

### Free Look

Hold Left Alt to look around in third person. Your character keeps facing where it was, so you keep walking and aiming the same way, and other players see no turn. Let go to return to the view you had.

- **Free Look Key:** any key or mouse button.
- **Free Look Mode:** Hold, or Toggle to press once to start and again to stop.

F5 back to first person also ends it. The Camera settings shape this view too.

### Fullbright

<sub>Off by default.</sub>
