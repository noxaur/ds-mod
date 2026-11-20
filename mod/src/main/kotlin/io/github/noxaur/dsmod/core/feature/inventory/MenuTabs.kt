package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.config.Canvas
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.ui.TabLayout

/**
 * Tabs along the top and bottom of menus that send the player's commands, and a search tab that dims the items that
 * do not match. It reads only the player's setup and the open menu's items, so all of it is `TabBar` and
 * the [tabs] panel's editor in the edge, with the rules in `TabLayout` and `ItemSearch`.
 */
class MenuTabs(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "menu-tabs", Area.ANYWHERE, Store.BETTER_UI, "Add tabs that send your commands, and a search, to every menu.")

    /** The tabs the player set up, in the editor. */
    val tabs = feature.stored("tabs", TabLayout.DEFAULT)

    init {
        feature.rows {
            panel(tabs.bind, "Tabs", "Press a place to add a tab there, and drag a tab to move it.", Canvas.MENU_TABS, id = "tabs") {
                if (it.size == 1) "1 tab" else "${it.size} tabs"
            }
        }
    }
}
