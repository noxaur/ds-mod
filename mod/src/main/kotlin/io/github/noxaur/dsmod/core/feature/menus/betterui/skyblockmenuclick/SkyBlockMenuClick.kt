package io.github.noxaur.dsmod.core.feature.menus.betterui.skyblockmenuclick

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

class SkyBlockMenuClick(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "skyblock-menu-click", Area.ANYWHERE, Store.BETTER_UI, "Click the SkyBlock Menu item to open the menu, not pick it up.")

    companion object {
        const val ITEM_ID = "skyblock_menu"
    }
}
