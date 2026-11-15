package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import kotlin.reflect.KMutableProperty0

/**
 * Adds a search field beside a menu that dims every item whose name and lore do not match. It reads only the open
 * menu's items, so all of it is `MenuTools` in the edge, with the rule in `ItemSearch`. This class puts it in the
 * list that the settings, the docs check and the tests go through.
 */
class MenuSearch(toggle: KMutableProperty0<Boolean>) : Feature("menu-search", toggle, Area.ANYWHERE)
