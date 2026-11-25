package io.github.noxaur.dragen.core.feature.general.dragen.dragsimonly

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area

class DragSimOnly(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, ID, Area.ANYWHERE, Store.GENERAL, "Run Dragen only on dragsim.co.", formerly = listOf("onlyOnDragSim"))

    companion object {
        const val ID = "dragsim-only"
    }
}
