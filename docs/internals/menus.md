# Menus

How the mod redraws the server's menus and adds controls to them. What each setting does for the player is in [docs/user/features.md](../user/features.md#inventory).

## The game's menus are redrawn, not retextured

Menu Theme draws a menu with the kit instead of recoloring its texture, for the reason the kit exists: one theme is a list of colors, and a texture per theme would be art to keep by hand. The mixins wrap only the call that blits the menu's texture. The rest of the screen still runs, so the inventory still draws the player, and Fabric's background event still fires for the slot marks. A screen whose texture holds more than a panel and slots, such as a furnace's flame or an anvil's field, is left alone. Drawing those in the kit is work for a screen the server does not open.

The recipe book's button is drawn by the kit where the game draws it, not replaced by a widget of the mod's, because the game moves that button itself when the book opens. The server's own Go Back, Close and page arrows are drawn as kit buttons in their slots, because a menu that mixes item buttons with the mod's reads as two systems. Only the drawing changes, so the click is still the player's own on the server's slot, and the server's tooltip stays. The server marks them with nothing but an item and a name, and it spells "Next page" in storage and "Next Page" in the auction house, so `MenuButton` lists each name a capture has shown.

A tooltip with its own `tooltip_style` keeps it. dragsim.co gives every item a style per rarity from its resource pack, so the kit's backing reaches only plain tooltips. Replacing all of them would erase the rarity frames the server draws.

## Glass is a look, not a feature

Glass is a look of Menu Theme and not a feature of its own, so the player finds everything about how a menu looks in one place, and a menu the kit cannot draw, such as a furnace, never turns half glass.

Glass is the HUD plate's dark fill at the player's alpha in both themes, not the theme's panel made see-through. Vanilla's light gray panel over a bright sky would leave its dark labels unreadable, while a dark sheet takes the light ink in either theme. A menu opened in the world dims the world with one gradient and no blur, and glass skips only that gradient, so every other screen keeps its dimming. The HUD shows through a glass menu as the world does, because seeing what is behind is the point of glass. Only the crosshair is hidden, since it would sit in the middle of the menu as a stray mark.

The buttons the kit draws on a menu turn to glass with it, so a glass menu does not carry opaque buttons from the theme. Glass tabs stand taller rather than join the panel: two see-through fills laid on each other show a darker band where they meet.

## Fixed Menu Layout keeps the bottom edge still

Fixed Menu Layout keeps a menu's bottom edge where a six-row menu's sits, not its top, because the server puts Go Back and Close in the bottom row of most menus and the player's inventory sits under it. Storage pages and the Ender Chest are the exception: they hold the player's items in every row below the top, so their Go Back, Close and page arrows sit in the top row. A chest menu's texture recenters itself on the screen and ignores the menu's top, so `ChestScreenMixin` moves the texture by the same amount. Without that the slots would move and the art would not.

Hide Glass Panes only skips drawing, so a click on a hidden pane still goes to the server.

## Keep Cursor cannot tell the server's close from the player's

The server closes a menu and opens the next in the same tick. Between the two the game has no screen, so it takes the mouse and then lets it go at the middle of the window. Keep Cursor puts the cursor back when a screen opens within half a second of the last one closing. It cannot tell a server's close from the player's, and does not need to: a player who reopens a menu that fast wants the cursor where it was too.

## Back Buttons resend the player's own command

Back Buttons can only resend a command. A menu from an NPC needs a click on the NPC, so a menu with no command earlier in its run gets no button. It resends the player's own command, unchanged, on the player's click. A command sent from inside a menu starts the run again, so Back leads to the newest command's menu.

The button covers Go Back's slot only while that slot holds filler. The server fills every slot it leaves unused, so an empty slot is one the player can put an item in, as each slot of a storage page is, and a button over it would take the click meant for the item.

## Menu Tabs are places on the menu's edges

Better UI's switch covers only the controls the mod adds to menus, not how the menus look, because a player who wants the vanilla controls back may still want dark, steady menus.

Menu Tabs are places along a menu's top and bottom edges, as creative tabs and the Skyblocker and SkyOcean tabs players know, so a tab dropped on another trades places with it instead of pushing a row along. The search bar hides the tabs it covers, so a click never lands on a tab under it. While the bar has focus it takes every key, because the menu's own keys would act on a typed letter.

The selected tab is the one whose command opened the menu shown now, which `MenuTrail` knows without matching the menu's title. Opening the recipe book moves the inventory and the crafting table sideways without laying the screen out again, so the tabs follow the menu's left edge on every frame instead of keeping where `init` put them.
