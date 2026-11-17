package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.ui.MenuLook

/**
 * Draws the game's menus, the player's inventory and plain tooltips in the player's `Theme`, which is dark in
 * Charcoal. It reads nothing from the server, so all of it is `Menus` and the menu mixins in the edge. This class
 * puts it in the list that the settings, the docs check and the tests go through.
 */
class MenuTheme(config: DsModConfig) :
    Feature(config, "menu-theme", Area.ANYWHERE, Store.INVENTORY, "Draw menus and plain tooltips in the Theme under Interface.", formerly = listOf("themedMenus")) {
    val menuLook = choice("menuLook", "Menu Look", "Draw themed menus solid, or as glass that shows the world.", MenuLook.SOLID)
    val glassOpacity = number("glassOpacity", "Glass Opacity", "Set how dark the glass is, from clear at 0 to solid at 100.", 60f, 0f..100f, step = 5f)
}
