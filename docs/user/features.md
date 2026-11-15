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

### Rarity Marks

Each item's slot is marked in the color of its rarity, in every menu and on your hotbar.

Rarity Style picks the look: Showcase, Gilded or Minimal, from loudest to quietest, or Custom for your own. Edit beside Edit Style opens your inventory with your hotbar under it, so you see each change on your own items. A change there starts from the look you had and switches Rarity Style to Custom.

| Part | Choices |
| --- | --- |
| Border | None, Flat, Square, Rounded, Gilded |
| Fill | None, Glow, Wash, Halo |
| Floor | None, Pedestal, Sparks, Rise |
| Gem | A small gem over the item's top-left corner |

The hotbar uses your inventory's look until you untick Match Inventory, and then it has its own.

<sub>Common items are not marked unless you tick Mark Common. With a resource pack that makes menus dark, the colors change to read well on it.</sub>

### Armor Swap Guard

<sub>Off by default. Sneak and right click to swap on purpose.</sub>

### Themed Menus

Menus, your inventory and plain tooltips take the colors of the Theme under Interface, so Charcoal makes them dark.

<sub>Chests, crafting tables, hoppers, dispensers and shulker boxes follow it. A furnace or an anvil keeps its own look, and item tooltips keep the server's rarity frames.</sub>

### Stable Menus

Go Back and Close sit in the same place in every menu, and so does your inventory. A shorter menu starts lower instead of moving its bottom row.

### Hide Filler

Hides the blank glass panes that pad out a menu, along with their tooltip and hover.

<sub>Colored panes, such as a terminal's, stay.</sub>

### Keep Cursor

When one menu opens another, the cursor stays where it was instead of jumping to the middle.

### Slot Lock

Locks slots and items of your inventory, so a misclick cannot move or drop them.

| Do this, in any menu | To |
| --- | --- |
| Press L over a slot of your inventory | Lock the slot, or unlock it. Whatever you put there is kept |
| Press K over an item in your inventory | Lock the item, or unlock it. The lock follows the item from slot to slot |
| Hold K and sweep the mouse over several items | Lock them all, or unlock them all when the first was locked |
| Hold L over a hotbar slot, move to a slot in the rows above, and let go | Link the two, which also locks both. Point at either to see its partner framed and joined by a line. The same again ends the link |
| Shift-click either slot of a link | Trade the two items' places |

A padlock marks each one: yellow for a slot, blue for a linked slot and green for an item.

- **What a lock stops:** clicks, number keys, the drop key and a double click that would gather from it. The drop key also keeps the item when no menu is open.
- **Shift-clicks elsewhere:** an item that would land in a locked slot goes to a free slot that is not locked, or stays put when there is none.
- **Which items:** only an item that does not stack, such as a weapon or armor, can be locked by itself. For a stack, lock its slot.
- **Armor and the off hand** can be locked too.

Slot Lock Key and Item Lock Key under Inventory in the settings take any key or mouse button. The server can still move a locked item itself, as when you sell it.

## Better UI

Tabs, a search and buttons that ds-mod adds to the game's menus. The Better UI switch turns them all off at once, and each has its own switch too.

### Back Buttons

A menu with no Go Back gets one in Go Back's usual place, if you reached it from a menu you opened with a command, such as `/storage`. Clicking it sends that command again. Hover it to see which command it sends.

<sub>A menu you opened from an NPC or an item gets none, because no command reopens it.</sub>

### Menu Tabs

Tabs along the top and bottom of every menu and your inventory. A tab sends its command when you click it, and the tab whose command opened the menu you're in joins the menu. It starts with Storage, Ender Chest, Crafting Table and Forge along the bottom, and Search at the top right.

Click Search, or press Ctrl+F, and a search bar opens along that edge toward the middle of the menu, over the tabs beside it. Every item whose name and lore don't hold all your words is dimmed, and your search stays as you move from menu to menu. While you type, E and Q type letters instead of closing the menu or dropping an item. Escape leaves the bar, a right click clears it, and its × closes it. The text turns red when nothing in the menu matches.

Set your tabs up with Edit Tabs, under Better UI:

| Do this | To |
| --- | --- |
| Drag a tab to another place | Move it. A tab already there takes its old place. Escape during the drag puts it back |
| Click an empty place | Add a tab there, then type its command |
| Click a tab | Change its command, its item, such as `chest` or `player_head`, and the name shown when you point at it |
| Arrow keys or Delete, with a tab chosen | Move it a place, or remove it |
| Add Search | Put the search tab back after you deleted it |
| Reset | Go back to the starting tabs |

<sub>Each edge has seven places. On a short screen at a large GUI scale, the tabs can run off the top or bottom of the screen.</sub>

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

### Command Binds

Sends a command with a key, with keys held together such as Shift + F6, or with keys pressed in turn such as F7 then F8. Open it with **Edit** beside Edit Command Binds in the settings.

Your binds are listed on the left under their groups, each with its command, its keys and where it works. Everything about the one you pick is on the right, and a change takes effect as you make it.

| Do this | To |
| --- | --- |
| Press **New Bind**, type the command, then press **Keys** and hold the keys | Add a bind. Let go of the keys to finish. Without a slash, the bind says its text in chat |
| Tick the box on a row | Turn the bind on or off |
| Tick the box on a group's heading | Turn the whole group on or off |
| Press the arrow on a heading, or double-click it | Fold the group away, or open it again |
| Drag a row or a heading, or press ▲ or ▼ beside Duplicate, or Alt and Up or Down | Move it. A bind moved past a group's heading joins that group |
| Right-click a row or a heading | See what you can do to it, such as Move Up or Turn Off |
| Press **Duplicate** or **Delete** | Copy the bind, which starts off, or delete it with a second press |

With nothing on the right typed in, Up and Down pick the next row, Left and Right fold and open a group, and Delete deletes.

On the right:

- **Name:** optional. The list shows the command without one.
- **Where:** in the world, in menus such as chests and your inventory, or in both. A bind never fires while you type in chat or a text field.
- **Priority:** when two binds share keys, the higher one sends, and between equals the one listed first.
- **Other Keys Held Too:** on by default, so a key you walk with does not stop the bind.
- **Keys in Order:** the keys become a sequence, finished with Escape when you record them. Key Sequence Gap in the settings sets how long a sequence waits for its next key.
- **Area:** Anywhere, or the one area the bind works in, such as The End.

**New Group** adds a group. Pick its heading to rename it, or to delete it with its binds. **Export** copies the group of whatever you picked to your clipboard to share, and **Import** adds a group someone shared. Imported binds start off, so read each command before you turn it on.

Keys shown in red fire another bind too, and keys in yellow also do something in the game. The line under the details says which.

### Explosion Particle Hider

<sub>Off by default.</sub>

### Free Look

Hold Left Alt to look around in third person. Your character keeps facing where it was, so you keep walking and aiming the same way, and other players see no turn. Let go to return to the view you had.

- **Free Look Key:** any key or mouse button.
- **Free Look Mode:** Hold, or Toggle to press once to start and again to stop.

F5 back to first person also ends it. The Camera settings shape this view too.

### Fullbright

<sub>Off by default.</sub>

## Contribute

ds-mod's developers cannot reach all of the server, so they ask players to record the parts they can't. Contribute is the last entry in the settings. It does nothing until you open it.

### Record a request

1. Open Contribute in the settings. The first time in a game it signs you in with your Minecraft account, which takes a second or two. Then a window shows one request you qualify for, with its steps and how long you have. A long request shows the steps that fit and how many more there are. A request that needs an area only shows once you have been there.
2. Press Accept to take it, or Skip if you don't want it. Skip hides it for you and shows the next one. Close takes you back to the game.
3. Your screen lists the steps and, under them, how much of the request's time is left. Press the key shown (`]` by default, Next Recording Step in the game's controls) to start, and press it again after each step. A step with a timer moves on by itself. ds-mod only tells you what to do. You type every command and make every move yourself.
4. After the last step, ds-mod sends the recording. Type `/dsmod submit` to send early, or `/dsmod cancel` to stop and send nothing.

If you take longer than the request allows, or you leave the server or the game, the recording is thrown away and nothing is sent. Changing area on the same server is fine. If sending fails, ds-mod keeps the recording and says so. Type `/dsmod submit` when no recording is running to send what it kept. If the request has closed since, ds-mod deletes the recording and tells you. If the recording could not be saved in full, it is thrown away, and you can accept the request again.

Before it leaves your computer, ds-mod removes player names and what players typed, and it does not send your Minecraft login. Several players record each request, so one run does not have to be perfect.

### Report Unread Lines

<sub>Off by default.</sub>

When you turn it on, lines the server sent that a feature could not read are sent to ds-mod's developers, with player names and what players typed removed, so a change on the server gets fixed sooner. The first goes about two minutes after one turns up, and then at most one report every twenty minutes. It sends nothing else.

## Account

### Sign in to the website

Type `/dsmod login` to open the ds-mod website in your browser, signed in as your Minecraft account. The link works once, for a minute, and is never shown in chat.
