package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import kotlin.reflect.KMutableProperty0

/**
 * Places every menu so its bottom row and the inventory under it stay put when the server opens one menu from
 * another. It reads nothing from the server, so all of it is `ContainerScreenMixin` and `ChestScreenMixin` in the
 * edge. This class puts it in the list that the settings, the docs check and the tests go through.
 */
class FixedMenuLayout(toggle: KMutableProperty0<Boolean>) : Feature("fixed-menu-layout", toggle, Area.ANYWHERE)
