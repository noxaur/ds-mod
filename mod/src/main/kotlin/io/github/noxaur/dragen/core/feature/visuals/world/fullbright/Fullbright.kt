package io.github.noxaur.dragen.core.feature.visuals.world.fullbright

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area

class Fullbright(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "fullbright", Area.ANYWHERE, Store.GENERAL, "Light caves and nights as brightly as day.", default = false)
}
