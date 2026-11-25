package io.github.noxaur.dragen.core.feature.menus.betterui.menutabs

import io.github.noxaur.dragen.core.config.Canvas
import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area
import io.github.noxaur.dragen.core.ui.TabLayout

class MenuTabs(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "menu-tabs", Area.ANYWHERE, Store.BETTER_UI, "Add tabs that send your commands, and a search, to every menu.")

    val tabs = feature.stored("tabs", TabLayout.DEFAULT)

    init {
        feature.rows {
            panel(tabs.bind, "Tabs", "Press a place to add a tab there, and drag a tab to move it.", Canvas.MENU_TABS, id = "tabs") {
                if (it.size == 1) "1 tab" else "${it.size} tabs"
            }
        }
    }
}
