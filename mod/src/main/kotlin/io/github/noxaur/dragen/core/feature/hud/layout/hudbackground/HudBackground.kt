package io.github.noxaur.dragen.core.feature.hud.layout.hudbackground

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area

class HudBackground(config: DragenConfig) : HasFeature {
    override val feature = Feature(
        config, "hud-background", Area.ANYWHERE, Store.HUD, "Show a dark background behind the HUD, to read it on bright blocks.", formerly = listOf("background"),
    )
}
