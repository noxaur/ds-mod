# Menus

How the mod adds controls to the server's menus. What each setting does for the player is in [docs/user/features.md](../user/features.md#inventory).

## Keep Cursor restores the cursor after any quick reopen

The server closes a menu and opens the next in one tick, and in between the game centers the mouse. So Keep Cursor puts the cursor back whenever a menu opens within half a second of one closing. Telling the server's close from the player's was rejected as needless, because a player who reopens a menu that fast wants the cursor where it was too.

## The menu trail hears each command as the game sends it

The menu trail notes every command the player sends, typed, clicked on a tab or a button, or fired by a bind, at the one place the game sends commands, so no sender has to tell it. A client command never reaches the server, so it opens no menu the trail follows, and missing one is right. Telling the trail from each of the mod's senders as well was rejected, because it could only note a client command, which selects no tab.

## SkyBlock Menu Click follows the ninth hotbar slot

SkyBlock Menu Click opens the menu only for a click on the SkyBlock Menu item in the ninth hotbar slot, because the server keeps the item there, and a player who hides the item gets the slot back for their own. Matching the item in any slot was rejected, so that a click on any other slot always behaves as the game's own.

## Menu Tabs are places on the menu's edges

Menu Tabs are places along a menu's top and bottom edges, like the creative tabs and the tabs of other mods players know. So a tab dropped on another trades places with it, instead of pushing a row along. The selected tab is the one whose command opened the menu, so matching the menu's title was not needed.

## The storage overview moves the server's slots into the open page

The storage overview keeps the server's menu as the screen and moves its slots into the open page's place, as Firmament does, so a click on the open page is the menu's own and every feature that marks or guards a slot still works there. A screen of the mod's own that passed clicks on to the menu was rejected, because each passed click would be the mod clicking for the player. The other pages are drawn from what the mod remembers, and a click on one sends that page's command.

## The storage overview scrolls above a still inventory

The storage overview ends where a six-row chest ends, so the player's inventory sits where every other menu has it and the hotbar stays in sight, and its pages scroll in the room above. Centering the panel on the screen and widening the grid until every page fit was rejected, because on a common screen it filled the whole height, moved the inventory down from menu to menu and covered the hotbar.
