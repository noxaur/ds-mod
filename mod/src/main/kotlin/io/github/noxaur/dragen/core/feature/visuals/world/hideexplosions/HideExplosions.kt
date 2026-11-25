package io.github.noxaur.dragen.core.feature.visuals.world.hideexplosions

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area

class HideExplosions(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "hide-explosions", Area.ANYWHERE, Store.GENERAL, "Hide explosion particles.", default = false, formerly = listOf("explosionParticleHider"))
}
