package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area

/**
 * Places every menu so its bottom row and the inventory under it stay put when the server opens one menu from
 * another. It reads nothing from the server, so all of it is `ContainerScreenMixin` and `ChestScreenMixin` in the
 * edge. This class puts it in the list that the settings, the docs check and the tests go through.
 */
class FixedMenuLayout(config: DsModConfig) :
    Feature(config, "fixed-menu-layout", Area.ANYWHERE, Store.INVENTORY, "Keep Go Back, Close and your inventory in place in every menu.", formerly = "stableMenus")
