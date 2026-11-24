package io.github.noxaur.dsmod.core.feature.hud.layout.hudbackground

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

class HudBackground(config: DsModConfig) : HasFeature {
    override val feature = Feature(
        config, "hud-background", Area.ANYWHERE, Store.HUD, "Show a dark plate behind the HUD, so it reads over a bright world.", formerly = listOf("background"),
    )
}
