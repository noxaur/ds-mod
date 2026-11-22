# Settings

How ds-mod's own settings screen draws every setting from one tree, and how the settings reach the disk.

## The tree is the only schema, and MoulConfig went

ds-mod draws its own settings screen and dropped MoulConfig. MoulConfig's boxes can't build a sidebar beside browser tabs with lists edited in place, its editors are not the kit's, so every control had to be built twice, and its annotations were a second copy of every name and description. So a setting is named, described and given its kind of row in one place, and the jar no longer carries a library.

## A setting is a row, and the screen builds the rest

Every setting is a row of one kind, and the screen builds everything a row does from its kind: the control, the changed marker, reset, dimming, search and the keyboard. An agent adds a setting with one line and writes no drawing or input code, so each kind's conventions are built once and hold for every setting. Each saved value has exactly one row, because a value two rows write disagrees with itself, and one with none can't be reached.

## A feature is declared in one place, and the tree keeps only the shape

A feature's switch, its settings and their defaults are declared in its own class, and the tree only places the feature. Four places that had to agree were rejected: a feature left out of one compiled and then silently had no row, no save or never ran. The tree still holds the shape, because where a feature sits among the sections is a choice about the screen, not about the feature. A feature placed under another's switch runs only while that switch is on, as its row dims, so a group's children never each check the group.

## A default lives beside the value it belongs to

A setting's default is declared in the one line that names its key, or read from a fresh config for a setting of no feature. A default written beside the row was rejected, because it would drift from the value's own, and the changed marker would then call an untouched setting changed.

## The config file keeps its keys

The config file keeps the keys and sections MoulConfig wrote, so a player who updates keeps every setting. Renaming a shipped key loses what players set, so a key that has to change names its old one. A file that does not parse is moved aside and the player is told where, because overwriting it would lose the settings without a word.

## The rows are drawn by one painter

All the rows are drawn by one painter, not a widget each. A widget can't be clipped to a scrolling pane or culled out of view, and a few hundred of them would each be asked every frame. The few controls that need a widget's own input exist only while the player has one open.

## A preview is a row that holds no value

Player Model's Preview shows the settings around it applied and edits nothing. A panel was the nearest kind, but a panel has a value, a changed marker and a reset, and every value already has its one row. So a preview is a kind of its own with no value, and it gets the name, dimming, search, star and keys of any row. Its turntable holds still, because Spin is one of the settings it shows.

## Every setting is edited where it sits

Lists, layouts and other editors that take many values are rows, not screens of their own reached through an Edit button. So a search finds them, and dimming and the keyboard work as on any row. A list of things the player made is one generic row, so the next list gets every convention without new drawing code. Only Move HUD opens a screen, because placing a block needs the world behind it.

## A list has no changed marker

A list row has no changed marker. Its default is an empty list, so a marker would offer to delete everything the player made with one press.

## Favorites are a section of the screen

Favorites is the first section of the sidebar, listing starred rows under their section's heading, so a row shows where it lives. A favorite is saved by its row's id, the same id a link and a HUD position use, so one setting has one id. A star shows only on a starred row and on the row under the mouse or keys, because a star on every row crowds the names.

## The Settings Key has no default

The Settings Key has no default, because DragSim Utils and Odin open on Right Shift, and a player who runs either would open two screens with one press. The short command leaves theirs alone for the same reason.
