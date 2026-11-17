# Settings

How ds-mod's own settings screen draws every setting from one tree, why it replaced MoulConfig's, how the settings reach the disk, and how favorites fit into the screen.

## The tree is the only schema, and MoulConfig went

The settings screen was MoulConfig's, the one SkyHanni uses, until ds-mod's own covered every setting. MoulConfig reads a setting's name, description and editor from annotations on its field, and lays every category out as one column of boxes it draws itself. Three things decided against keeping it. The own screen's layout, a sidebar of sections beside browser tabs with lists and canvases edited in place, can't be built from its boxes. Its editors are not the kit's, so a control's conventions, its look in both themes and its narration had to be built twice, once per screen. And the annotations were a second copy of every name and description, which `SettingsTreeTest` had to hold to the tree's. So a setting is named, described and given its kind of row in one place, which is its feature's class for a feature's setting and `SettingsTree` for a setting of no feature, and `/dsmod`, the Settings Key and Mod Menu's config button all open `SettingsScreen` through `DsModClient.openSettings`. MoulConfig also shipped inside the release jar, so the jar is smaller without it.

The editors that only MoulConfig's buttons opened went with it. Beam Timing's screen is the Span row under Protector Spawn Beam now, and the buttons that opened the own screen at a list are gone, since the list is a row where the player already is.

## The config file keeps MoulConfig's file and keys

`ConfigFile` writes `config/dsmod/config.json` with kotlinx.serialization, which the mod's saved state already uses, keyed by the field names, the same keys MoulConfig's Gson wrote. A feature's settings are no longer fields: each is a `Slot` the feature declares, saved under its key in the feature's `Store`, which is the section of the file its field always sat in. So a feature under Combat in the screen still writes to `general` in the file, and the file looks as it did. The slots are written into the same JSON objects as the fields, and read as the features are built, since only the feature knows a slot's type. A player who updates keeps every setting, and `ConfigFileTest` proves it on a file 0.1.0 wrote with every setting changed. A key is what the file holds, so renaming one that shipped loses what players set, and a slot that has to change its key names the old one as `formerly`, as a field names it in `@JsonNames`.

The decoder keeps what Gson did for a key the file lacks, which keeps its default, and a key no field has, which is passed over. It goes further for a value an enum no longer has, which takes the field's default where Gson wrote null into a field Kotlin holds non-null. A file that does not parse at all is renamed `config.json.corrupt-<time>` and the settings start from their defaults, and the player is told once in chat where the old file went. MoulConfig started from the defaults too, but left the bad file in place for its next save to overwrite, so the settings were lost without a word.

MoulConfig saved only fields marked `@Expose`, and `SlotStyle` had none, so a custom rarity look was written as `{}` and lost on every restart. The serializer writes every field of a `@Serializable` class, so that loss is gone, and an old file's `{}` still reads as the default look.

## Favorites are a section of the screen

Favorites is the first section of the sidebar, made by the screen and not the tree, since it holds no settings of its own. It lists the starred rows under "Section › Subsection" headings, as a search lists its hits, so a row shows where it lives. A row under a starred row is left out, since it opens under it. What a favorite is lives in `Favorites` and `FavoritesView` in `core`: the order of the tree, that a row unstarred inside Favorites stays until the player leaves, so a quick second press stars it again, and that the first open of a session shows the first section of the tree while nothing is starred, rather than an empty page. A search finds nothing in Favorites, so each match shows once, in its own section.

A favorite is saved by its row's id, the id a link to the setting uses. A feature's switch has the feature's id, as `rare-drop-alert`, so the id a favorite, a link and a HUD position use is one id. The switch used to have its field's name, `rareDropAlert`, and MoulConfig saved a favorite by its option's path, such as `general.fullbright`, so `SettingsTree.favorites` turns either, by the current or a former key, into the id of the row that edits that field, or of the row that took over a button's job, when the config loads.

A star shows beside a row's name only on a starred row and on the row under the mouse or the keys. A star on every row would crowd the names, as MoulConfig's faint stars did. The Favorite Key acts on the row under the mouse, or on the row the keys are on while they show, so the keyboard alone can star a setting. Typing anywhere searches, so the key's character is swallowed after a press that starred a row, and the key is left alone while the search has the keys or Ctrl or Alt is held, so typing and Ctrl+F still work.

The Settings Key has no default. DragSim Utils and Odin open on Right Shift, and a player who runs either would open two screens with one press. `/dsm` is the short name and `/du` is left alone for the same reason.

## The settings tree is the schema of the settings

Every setting is a row of one kind, and the own screen builds everything a row does from its kind: the control, the changed marker, reset, dimming, search and the keyboard. An agent adds a setting with one line and writes no drawing or input code, so the conventions of each kind are built once and hold for every setting. `SettingsTreeTest` checks that each saved field and slot has exactly one row, because one that two rows write disagrees with itself, and one with none is a setting the player can't reach.

A row of a setting that belongs to no feature, such as the theme, is bound to a field of `DsModConfig`, and its default is the field's value in a fresh config, read by building the tree a second time on one. A default written beside the row would drift from the field's own initializer, and the marker would then call an untouched setting changed. A feature's row is declared with its default, in the one line that also names the key, so there is no second copy to drift from. A part of a look is the exception: while a preset is showing, or the hotbar is matching the inventory, its default is that look, so a reset puts the part back as it is shown and does not switch Style to Custom or turn Match Inventory off.

The rows are drawn by one painter, `SettingRows`, not a vanilla widget each. A widget can't be clipped to a scrolling pane or culled when it is out of view, and a few hundred of them would each be asked every frame. The painter lays the rows out when what is shown changes, keeps each row's words and widths from then, and draws only the rows in view. The few controls that need a widget's own input, a number's field and a key being recorded, are made when the player opens one and dropped when it closes.

A pick of a rarity style shows previews of styles the player has not chosen, and a texture per style can't be made while a frame is drawn. `Looks` paints one atlas at the end of a tick, with each border, fill and floor alone, and draws a style as three blits. `SlotArt` paints a style back to front with straight alpha, so the layers blitted in that order give the pixels of the style painted whole.

## A feature is declared in one place, and the tree keeps only the shape

A feature's switch, its settings and their defaults are declared in its own class, and `SettingsTree` only places the feature: as a group of its own, or a row among other features. Each feature used to be four places that had to agree: a field per setting in `DsModConfig`, a row per setting in `SettingsTree`, a constructor that bound each field to the feature, and a line in a `features()` list. A feature left out of one of them compiled and then silently had no row, no save or never ran, and the row's name, the feature's title and the saved key were typed apart. Now the list of features is read off the tree, so a feature placed nowhere is one the `FeaturesListedTest` scan catches, and a switch with no row cannot be written. The row's id and title are the feature's id and title. Adding a feature is one file, its fixture, its README line and its docs section, and the example an agent copies is any feature file.

The tree still holds the shape, because where a feature sits among the sections is a choice about the screen, not about the feature: Damage Numbers saves under `general` but shows under Combat. Both forms of placement, the group and the nested row, build on the same `Switch`, which takes the feature's rows as its children when it sits among other features.

## A preview is a row that holds no value

Player Model's Preview shows the player with the settings around it applied, and edits nothing. A Panel was the nearest kind, but a Panel has a value, a changed marker and a reset, and `SettingsTreeTest` holds every value to exactly one saved field, which the rows beside it already reach. So `Preview` is a kind of its own with no value, drawn by an `Editor` the edge registers for its `Canvas`, as a Panel's is, and it gets the row's name, dimming, search, star and keys like any row.

The preview's turntable holds still where the profile viewer's turns on its own, because Spin is one of the settings it shows, and a turn of its own would hide whether the model spins.

## Every setting is edited where it sits

The command binds, the menu tabs, the HUD layout, the hotbar's own look and Beam Timing each had a screen of their own, which a player reached through an Edit button and left again to see the rest. They are rows now, so a search finds them and the changed marker, dimming and the keyboard work on them as on any row. An editor that takes many values in place is an `Editor` that `SettingRows` hosts under the row's header: it lays out on change, takes the input only while the player is inside it, and gives Escape back. A list of things the player made is one generic Items row over an `ItemKind` in `core`, so the next list gets every convention of the binds without a line of drawing code. Only Move HUD still opens a screen, because placing a block needs the world behind it.

An Items row has no changed marker. Its default is an empty list, so a marker would offer to delete everything the player made with one press.

The HUD layout draws the real blocks under a scaled pose, and turns a point on the copy back into a point of the game's screen, so `HudMover` moves a block there by the same rules as in the HUD editor and on the chat screen. A second set of rules for the copy would drift from those.
