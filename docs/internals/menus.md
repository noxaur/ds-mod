# Menus

How the mod redraws the server's menus and adds controls to them. What each setting does for the player is in [docs/user/features.md](../user/features.md#inventory).

## The game's menus are redrawn, not retextured

Menu Theme draws a menu with the kit instead of recoloring its texture, because a theme is a list of colors, and a texture per theme would be art to keep by hand. Only the texture's draw is replaced, so the rest of the screen still runs. The server's own buttons are drawn as kit buttons in their slots, because a menu that mixes item buttons with the mod's reads as two systems, and the click still goes to the server's slot.

## Every container screen of the game is themed

Menu Theme draws every one of the game's own container screens, a furnace's flame and an anvil's field included, because a vanilla menu beside themed ones reads as two systems. The game names each part it draws, such as a progress arrow or a scrollbar, so the mod swaps each named part for the kit's shape, and one place decides which screens are themed. Leaving a screen whose texture holds more than a panel and slots alone was rejected, and so was a list of screens kept in two places. A screen another mod adds keeps its own art.

## The server's tooltip styles are kept

A tooltip with its own style keeps it, because dragsim.co gives every item a frame per rarity from its resource pack. Backing every tooltip with the kit was rejected, because it would erase the rarity frames the server draws.

## Glass is a look, not a feature

Glass is a look of Menu Theme, so the player finds everything about how a menu looks in one place. Glass is the HUD plate's dark fill at the player's alpha, not the theme's panel made see-through, because a light panel over a bright sky leaves its dark labels unreadable, while a dark sheet takes the light ink in either theme.

## Fixed Menu Layout keeps the bottom edge still

Fixed Menu Layout keeps a menu's bottom edge where a six-row menu's sits, not its top, because the server puts Go Back and Close in the bottom row of most menus, with the player's inventory under it. Storage pages and the Ender Chest keep their top still instead, because there the controls sit in the top row.

## Keep Cursor restores the cursor after any quick reopen

The server closes a menu and opens the next in one tick, and in between the game centers the mouse. So Keep Cursor puts the cursor back whenever a menu opens within half a second of one closing. Telling the server's close from the player's was rejected as needless, because a player who reopens a menu that fast wants the cursor where it was too.

## Back Buttons resend the player's own command

Back Buttons resend the player's own command, unchanged, on the player's click, so the mod never acts in the player's place. A menu with no command earlier in its run gets no button, because a menu from an NPC needs a click on the NPC. The button covers Go Back's slot only while it holds filler, because an empty slot is one the player can put an item in.

## The menu trail hears each command as the game sends it

The menu trail notes every command the player sends, typed, clicked on a tab or a button, or fired by a bind, at the one place the game sends commands, so no sender has to tell it. A client command never reaches the server, so it opens no menu the trail follows, and missing one is right. Telling the trail from each of the mod's senders as well was rejected, because it could only note a client command and offer a Go Back that reopens nothing.

## SkyBlock Menu Click follows the ninth hotbar slot

SkyBlock Menu Click opens the menu only for a click on the SkyBlock Menu item in the ninth hotbar slot, because the server keeps the item there, and a player who hides the item gets the slot back for their own. Matching the item in any slot was rejected, so that a click on any other slot always behaves as the game's own.

## Better UI covers the controls, not the look

Better UI's switch covers only the controls the mod adds to menus, not how the menus look, because a player who wants the vanilla controls back may still want dark, steady menus.

## Menu Tabs are places on the menu's edges

Menu Tabs are places along a menu's top and bottom edges, like the creative tabs and the tabs of other mods players know. So a tab dropped on another trades places with it, instead of pushing a row along. The selected tab is the one whose command opened the menu, so matching the menu's title was not needed.
