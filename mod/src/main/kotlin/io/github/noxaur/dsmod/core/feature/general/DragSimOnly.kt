package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

class DragSimOnly(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, ID, Area.ANYWHERE, Store.GENERAL, "Run ds-mod only on dragsim.co.", formerly = listOf("onlyOnDragSim"))

    companion object {
        const val ID = "dragsim-only"
    }
}
