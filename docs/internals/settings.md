# Settings

How ds-mod's own settings screen draws every setting from one tree, why it replaced MoulConfig's, how the settings reach the disk, and how favorites fit into the screen.

## The tree is the only schema, and MoulConfig went

The settings screen was MoulConfig's, the one SkyHanni uses, until ds-mod's own covered every setting. MoulConfig reads a setting's name, description and editor from annotations on its field, and lays every category out as one column of boxes it draws itself. Three things decided against keeping it. The own screen's layout, a sidebar of sections beside browser tabs with lists and canvases edited in place, can't be built from its boxes. Its editors are not the kit's, so a control's conventions, its look in both themes and its narration had to be built twice, once per screen. And the annotations were a second copy of every name and description, which `SettingsTreeTest` had to hold to the tree's. So `DsModConfig` is plain data, `SettingsTree` is the one place a setting is named, described and given its kind of row, and `/dsmod`, the Settings Key and Mod Menu's config button all open `SettingsScreen` through `DsModClient.openSettings`. MoulConfig also shipped inside the release jar, so the jar is smaller without it.

The editors that only MoulConfig's buttons opened went with it. Beam Timing's screen is the Span row under Boss Beacon now, and the buttons that opened the own screen at a list are gone, since the list is a row where the player already is.

## The config file keeps MoulConfig's file and keys

`ConfigFile` writes `config/dsmod/config.json` with kotlinx.serialization, which the mod's saved state already uses, keyed by the field names, the same keys MoulConfig's Gson wrote. A player who updates keeps every setting, and `ConfigFileTest` proves it on a file 0.1.0 wrote with every setting changed. A field's name is its key, so renaming one that shipped loses what players set.

The decoder keeps what Gson did for a key the file lacks, which keeps its default, and a key no field has, which is passed over. It goes further for a value an enum no longer has, which takes the field's default where Gson wrote null into a field Kotlin holds non-null. A file that does not parse at all is renamed `config.json.corrupt-<time>` and the settings start from their defaults, and the player is told once in chat where the old file went. MoulConfig started from the defaults too, but left the bad file in place for its next save to overwrite, so the settings were lost without a word.

MoulConfig saved only fields marked `@Expose`, and `SlotStyle` had none, so a custom rarity look was written as `{}` and lost on every restart. The serializer writes every field of a `@Serializable` class, so that loss is gone, and an old file's `{}` still reads as the default look.

## Favorites are a section of the screen

Favorites is the first section of the sidebar, made by the screen and not the tree, since it holds no settings of its own. It lists the starred rows under "Section › Subsection" headings, as a search lists its hits, so a row shows where it lives. A row under a starred row is left out, since it opens under it. What a favorite is lives in `Favorites` and `FavoritesView` in `core`: the order of the tree, that a row unstarred inside Favorites stays until the player leaves, so a quick second press stars it again, and that the first open of a session shows the first section of the tree while nothing is starred, rather than an empty page. A search finds nothing in Favorites, so each match shows once, in its own section.

A favorite is saved by its row's id, the id a link to the setting uses. MoulConfig saved one by its option's path, such as `general.fullbright`, so `SettingsTree.favorites` turns a path into the id of the row that edits that field, or of the row that took over a button's job, when the config loads.

A star shows beside a row's name only on a starred row and on the row under the mouse or the keys. A star on every row would crowd the names, as MoulConfig's faint stars did. The Favorite Key acts on the row under the mouse, or on the row the keys are on while they show, so the keyboard alone can star a setting. Typing anywhere searches, so the key's character is swallowed after a press that starred a row, and the key is left alone while the search has the keys or Ctrl or Alt is held, so typing and Ctrl+F still work.

The Settings Key has no default. DragSim Utils and Odin open on Right Shift, and a player who runs either would open two screens with one press. `/dsm` is the short name and `/du` is left alone for the same reason.

## The settings tree is the schema of the settings

`SettingsTree` binds each saved field of `DsModConfig` to a kind of row, and the own screen builds everything a row does from its kind: the control, the changed marker, reset, dimming, search and the keyboard. An agent adds a setting with one line and writes no drawing or input code, so the conventions of each kind are built once and hold for every setting. `SettingsTreeTest` checks that each saved field has exactly one row, because a field that two rows write disagrees with itself, and a field with none is a setting the player can't reach.

A row's default is its value in a fresh `DsModConfig`, read by building the tree a second time on one. A default written beside the row would drift from the field's own initializer, and the marker would then call an untouched setting changed. A part of a look is the exception: while a preset is showing, or the hotbar is matching the inventory, its default is that look, so a reset puts the part back as it is shown and does not switch Style to Custom or turn Match Inventory off.

The rows are drawn by one painter, `SettingRows`, not a vanilla widget each. A widget can't be clipped to a scrolling pane or culled when it is out of view, and a few hundred of them would each be asked every frame. The painter lays the rows out when what is shown changes, keeps each row's words and widths from then, and draws only the rows in view. The few controls that need a widget's own input, a number's field and a key being recorded, are made when the player opens one and dropped when it closes.

A pick of a rarity style shows previews of styles the player has not chosen, and a texture per style can't be made while a frame is drawn. `Looks` paints one atlas at the end of a tick, with each border, fill and floor alone, and draws a style as three blits. `SlotArt` paints a style back to front with straight alpha, so the layers blitted in that order give the pixels of the style painted whole.

## Every setting is edited where it sits

The command binds, the menu tabs, the HUD layout, the hotbar's own look and Beam Timing each had a screen of their own, which a player reached through an Edit button and left again to see the rest. They are rows now, so a search finds them and the changed marker, dimming and the keyboard work on them as on any row. An editor that takes many values in place is an `Editor` that `SettingRows` hosts under the row's header: it lays out on change, takes the input only while the player is inside it, and gives Escape back. A list of things the player made is one generic Items row over an `ItemKind` in `core`, so the next list gets every convention of the binds without a line of drawing code. Only Move HUD still opens a screen, because placing a block needs the world behind it.

An Items row has no changed marker. Its default is an empty list, so a marker would offer to delete everything the player made with one press.

The HUD layout draws the real blocks under a scaled pose, and turns a point on the copy back into a point of the game's screen, so `HudMover` moves a block there by the same rules as in the HUD editor and on the chat screen. A second set of rules for the copy would drift from those.
