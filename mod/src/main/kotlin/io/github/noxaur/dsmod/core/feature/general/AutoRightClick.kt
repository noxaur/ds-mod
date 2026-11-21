package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

class AutoRightClick(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "auto-right-click", Area.ANYWHERE, Store.GENERAL, "Right click 9.9 times a second on a key while you walk.", default = false)

    val click = AutoClick(feature, "autoRightClick", "right", "Auto Left Click", MOUSE)

    private companion object {
        const val MOUSE = 1
    }
}
