package io.github.noxaur.dsmod.core.feature.menus.betterui

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Groups
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.feature.menus.betterui.hotdbutton.HotdButton
import io.github.noxaur.dsmod.core.feature.menus.betterui.menutabs.MenuTabs
import io.github.noxaur.dsmod.core.feature.menus.betterui.skyblockmenuclick.SkyBlockMenuClick
import io.github.noxaur.dsmod.core.game.Area

internal fun Groups.betterUi() = group(BetterUi(c)) {
    feature(MenuTabs(c))
    feature(SkyBlockMenuClick(c))
    feature(HotdButton(c))
}

private class BetterUi(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "better-ui", Area.ANYWHERE, Store.BETTER_UI, "Turn the controls the mod adds to menus on or off at once.", formerly = listOf("enabled"))
}
