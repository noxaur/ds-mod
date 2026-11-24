package io.github.noxaur.dsmod.core.feature.menus.betterui.menutabs

import io.github.noxaur.dsmod.core.config.Canvas
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.ui.TabLayout

class MenuTabs(config: DsModConfig) : HasFeature {
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
