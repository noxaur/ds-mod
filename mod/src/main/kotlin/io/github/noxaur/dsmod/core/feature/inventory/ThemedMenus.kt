package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import kotlin.reflect.KMutableProperty0

/**
 * Draws the game's menus, the player's inventory and plain tooltips in the player's `Theme`, which is dark in
 * Charcoal. It reads nothing from the server, so all of it is `Menus` and the menu mixins in the edge. This class
 * puts it in the list that the settings, the docs check and the tests go through.
 */
class ThemedMenus(toggle: KMutableProperty0<Boolean>) : Feature("themed-menus", toggle, Area.ANYWHERE)
