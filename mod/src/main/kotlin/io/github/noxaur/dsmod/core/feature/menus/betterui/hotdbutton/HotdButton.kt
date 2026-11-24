package io.github.noxaur.dsmod.core.feature.menus.betterui.hotdbutton

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

class HotdButton(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "hotd-button", Area.ANYWHERE, Store.BETTER_UI, "Add a Heart of the Dragon button to the Dragon Sim Menu.")
}
