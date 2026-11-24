package io.github.noxaur.dsmod.core.feature.hud.layout.hideinf3

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

class HideInF3(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "hide-in-f3", Area.ANYWHERE, Store.HUD, "Hide ds-mod's HUD while the F3 debug screen is open.", default = false)
}
