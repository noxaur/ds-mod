package io.github.noxaur.dragen.core.feature.contribute.reportgamedata

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area

class ReportGameData(config: DragenConfig) : HasFeature {
    override val feature = Feature(
        config, "report-game-data", Area.ANYWHERE, Store.CONTRIBUTE, "Send game data new to Dragen to its developers, names removed.", default = false,
    )
}
