package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

/**
 * Hides the clouds an explosion throws up, which cover the boss in a crowded fight. It reads nothing from the
 * server, so all of it is `ExplosionParticleMixin` in the edge.
 */
class HideExplosions(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "hide-explosions", Area.ANYWHERE, Store.GENERAL, "Hide the clouds that explosions throw up.", default = false, formerly = listOf("explosionParticleHider"))
}
