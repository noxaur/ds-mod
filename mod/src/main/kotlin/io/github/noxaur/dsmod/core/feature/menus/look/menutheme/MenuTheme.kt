package io.github.noxaur.dsmod.core.feature.menus.look.menutheme

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.ui.MenuLook

class MenuTheme(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "menu-theme", Area.ANYWHERE, Store.INVENTORY, "Draw menus and plain tooltips in the Theme under General.", formerly = listOf("themedMenus"))

    val menuLook = feature.choice("menuLook", "Menu Look", "Draw themed menus solid, or as glass that shows the world.", MenuLook.SOLID)
    val glassOpacity = feature.number("glassOpacity", "Glass Opacity", "Set how dark the glass is, from clear at 0 to solid at 100.", 60f, 0f..100f, step = 5f)
}
