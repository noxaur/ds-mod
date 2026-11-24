package io.github.noxaur.dsmod.core.feature.contribute.reportgamedata

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

class ReportGameData(config: DsModConfig) : HasFeature {
    override val feature = Feature(
        config, "report-game-data", Area.ANYWHERE, Store.CONTRIBUTE, "Send game data new to ds-mod to its developers, names removed.", default = false,
    )
}
