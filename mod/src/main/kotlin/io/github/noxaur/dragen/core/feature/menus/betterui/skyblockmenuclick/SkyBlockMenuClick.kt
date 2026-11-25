package io.github.noxaur.dragen.core.feature.menus.betterui.skyblockmenuclick

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area

class SkyBlockMenuClick(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "skyblock-menu-click", Area.ANYWHERE, Store.BETTER_UI, "Open the SkyBlock Menu when you click its item, not pick it up.")

    companion object {
        const val ITEM_ID = "skyblock_menu"
    }
}
