package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

class HideExplosions(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "hide-explosions", Area.ANYWHERE, Store.GENERAL, "Hide the clouds that explosions throw up.", default = false, formerly = listOf("explosionParticleHider"))
}
