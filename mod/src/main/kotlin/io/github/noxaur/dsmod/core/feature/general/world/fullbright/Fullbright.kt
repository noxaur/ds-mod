package io.github.noxaur.dsmod.core.feature.general.world.fullbright

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

class Fullbright(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "fullbright", Area.ANYWHERE, Store.GENERAL, "Light caves and nights as brightly as day.", default = false)
}
