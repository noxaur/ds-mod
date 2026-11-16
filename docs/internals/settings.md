# Settings

How the settings screen is built on MoulConfig, how favorites fit into it, and how ds-mod's own screen, which is to replace it, draws every setting from one tree. Why MoulConfig ships inside the jar is in the [overview](overview.md#the-release-jar-carries-its-own-moulconfig).

## Favorites wrap MoulConfig's categories and options

What a favorite is lives in `Favorites` and `FavoritesView` in `core`: which settings are starred, kept with the config by path, their order, that a search hides them, and that a row unstarred inside Favorites stays until the player leaves it. The settings screen only shows it, because ds-mod's own screen is to replace MoulConfig's, and `Settings.open` is the one way into whichever is the release screen. The star and the wrapping below are MoulConfig's half, which goes with it.

MoulConfig has no favorites, and its editor takes any categories and options it is handed. So `Settings` hands it the player's categories with each option wrapped, the wrapper's editor drawing a star over the real one, and a Favorites category in front whose options are the same wrapped ones. A wrapped option names its own category as its home, not Favorites, which is how a search shows each setting once: MoulConfig lists during a search only the categories its matches name. An option's category must be the wrapped one, because MoulConfig compares categories by identity. An accordion keeps its own editor, since MoulConfig finds one by its class.

MoulConfig shows only the options it was handed when the editor was built, so the row an empty Favorites shows is handed over then too, even when the player has stars, or unstarring the last one later would bring back MoulConfig's own empty text, which blames a search.

The star sits in the gap between the name and the description. MoulConfig fits a name within 5 of the left third's edges and starts the description 5 past it, so the star covers neither however long either runs. A press is hit-tested against the position MoulConfig passes with it, because an option scrolled out of view keeps the place it was last drawn.

MoulConfig gives a setting no focus, so the Favorite Key acts on the setting under the mouse. MoulConfig hands a key to every setting before its search field, and a typed character moves focus to the search, so the key is left alone while the shared `GuiContext` has a focused element, and the character that follows a press is swallowed. The setting under the mouse is the one drawn there this frame with the mouse inside the scissor MoulConfig draws the settings in, because MoulConfig hands mouse events to every setting, and one scrolled partly under the header would otherwise take the key and a press on its hidden star.

One editor lasts the session, as SkyHanni keeps one, so the settings open on the category, search and scroll the player left. MoulConfig 4.8 tells only a component that the screen closed, and the editor sits inside one that does not pass it on, so `Settings.open` opens it in a context that does. Without it, a change made in the settings is written only when another screen saves.

The Settings Key has no default. DragSim Utils and Odin open on Right Shift, and a player who runs either would open two screens with one press. `/dsm` is the short name and `/du` is left alone for the same reason.

## The settings tree is the schema of the settings

`SettingsTree` binds each saved field of `DsModConfig` to a kind of row, and the own screen builds everything a row does from its kind: the control, the changed marker, reset, dimming, search and the keyboard. An agent adds a setting with one line and writes no drawing or input code, so the conventions of each kind are built once and hold for every setting. `SettingsTreeTest` checks that each saved field has exactly one row, because a field that two rows write disagrees with itself, and a field with none is a setting the player can't reach.

A row's default is its value in a fresh `DsModConfig`, read by building the tree a second time on one. A default written beside the row would drift from the field's own initializer, and the marker would then call an untouched setting changed.

The rows are drawn by one painter, `SettingRows`, not a vanilla widget each. A widget can't be clipped to a scrolling pane or culled when it is out of view, and a few hundred of them would each be asked every frame. The painter lays the rows out when what is shown changes, keeps each row's words and widths from then, and draws only the rows in view. The few controls that need a widget's own input, a number's field and a key being recorded, are made when the player opens one and dropped when it closes.

A pick of a rarity style shows previews of styles the player has not chosen, and a texture per style can't be made while a frame is drawn. `Looks` paints one atlas at the end of a tick, with each border, fill and floor alone, and draws a style as three blits. `SlotArt` paints a style back to front with straight alpha, so the layers blitted in that order give the pixels of the style painted whole.

## Every setting is edited where it sits

The command binds, the menu tabs, the HUD layout and the hotbar's own look each had a screen of their own, which a player reached through an Edit button and left again to see the rest. They are rows now, so a search finds them and the changed marker, dimming and the keyboard work on them as on any row. An editor that takes many values in place is an `Editor` that `SettingRows` hosts under the row's header: it lays out on change, takes the input only while the player is inside it, and gives Escape back. A list of things the player made is one generic Items row over an `ItemKind` in `core`, so the next list gets every convention of the binds without a line of drawing code. Only Move HUD still opens a screen, because placing a block needs the world behind it.

An Items row has no changed marker. Its default is an empty list, so a marker would offer to delete everything the player made with one press.

The HUD layout draws the real blocks under a scaled pose, and turns a point on the copy back into a point of the game's screen, so `HudMover` moves a block there by the same rules as in the HUD editor and on the chat screen. A second set of rules for the copy would drift from those.

MoulConfig's Edit buttons for these open the own screen at the row, over MoulConfig's, so each list has one editor and Escape goes back to MoulConfig.
