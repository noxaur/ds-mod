package io.github.noxaur.dragen.core.feature.hud.layout.hideinf3

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area

class HideInF3(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "hide-in-f3", Area.ANYWHERE, Store.HUD, "Hide Dragen's HUD while the F3 debug screen is open.", default = false)
}
