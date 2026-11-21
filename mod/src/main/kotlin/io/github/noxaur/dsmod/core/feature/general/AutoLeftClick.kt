package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

class AutoLeftClick(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "auto-left-click", Area.ANYWHERE, Store.GENERAL, "Left click 9.9 times a second on a key while you walk.", default = false)

    val click = AutoClick(feature, "autoLeftClick", "left", "Auto Right Click", MOUSE)

    private companion object {
        const val MOUSE = 0
    }
}
