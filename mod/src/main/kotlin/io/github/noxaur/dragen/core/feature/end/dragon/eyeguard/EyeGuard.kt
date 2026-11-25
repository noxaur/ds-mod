package io.github.noxaur.dragen.core.feature.end.dragon.eyeguard

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area

class EyeGuard(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "eye-guard", Area.END, Store.END, "Stop a quick second click from taking back an eye you placed.", formerly = listOf("protectPlacedEyes"))
}
