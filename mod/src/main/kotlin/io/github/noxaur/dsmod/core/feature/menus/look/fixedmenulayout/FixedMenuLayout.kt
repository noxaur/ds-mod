package io.github.noxaur.dsmod.core.feature.menus.look.fixedmenulayout

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

class FixedMenuLayout(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "fixed-menu-layout", Area.ANYWHERE, Store.INVENTORY, "Keep Go Back, Close and your inventory in place in every menu.", formerly = listOf("stableMenus"))
}
