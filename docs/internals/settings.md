# Settings

How the settings screen is built on MoulConfig, and how favorites fit into it. Why MoulConfig ships inside the jar is in the [overview](overview.md#the-release-jar-carries-its-own-moulconfig).

## Favorites wrap MoulConfig's categories and options

What a favorite is lives in `Favorites` and `FavoritesView` in `core`: which settings are starred, kept with the config by path, their order, that a search hides them, and that a row unstarred inside Favorites stays until the player leaves it. The settings screen only shows it, because ds-mod's own screen is to replace MoulConfig's, and `Settings.open` is the one way into whichever is the release screen. The star and the wrapping below are MoulConfig's half, which goes with it.

MoulConfig has no favorites, and its editor takes any categories and options it is handed. So `Settings` hands it the player's categories with each option wrapped, the wrapper's editor drawing a star over the real one, and a Favorites category in front whose options are the same wrapped ones. A wrapped option names its own category as its home, not Favorites, which is how a search shows each setting once: MoulConfig lists during a search only the categories its matches name. An option's category must be the wrapped one, because MoulConfig compares categories by identity. An accordion keeps its own editor, since MoulConfig finds one by its class.

MoulConfig shows only the options it was handed when the editor was built, so the row an empty Favorites shows is handed over then too, even when the player has stars, or unstarring the last one later would bring back MoulConfig's own empty text, which blames a search.

The star sits in the gap between the name and the description. MoulConfig fits a name within 5 of the left third's edges and starts the description 5 past it, so the star covers neither however long either runs. A press is hit-tested against the position MoulConfig passes with it, because an option scrolled out of view keeps the place it was last drawn.

MoulConfig gives a setting no focus, so the Favorite Key acts on the setting under the mouse. MoulConfig hands a key to every setting before its search field, and a typed character moves focus to the search, so the key is left alone while the shared `GuiContext` has a focused element, and the character that follows a press is swallowed. The setting under the mouse is the one drawn there this frame with the mouse inside the scissor MoulConfig draws the settings in, because MoulConfig hands mouse events to every setting, and one scrolled partly under the header would otherwise take the key and a press on its hidden star.

One editor lasts the session, so the settings open on the category, search and scroll the player left. MoulConfig 4.8 tells only a component that the screen closed, and the editor sits inside one that does not pass it on, so `Settings.open` opens it in a context that does. Without it, a change made in the settings is written only when another screen saves.

The Settings Key has no default. Other mods open on Right Shift, and a player who runs one would open two screens with one press. `/dsm` is the short name and `/du` is left alone for the same reason, because another mod has it.
