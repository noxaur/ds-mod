package io.github.noxaur.dragen.core.feature.general.mouse.ctrlclick

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area

class CtrlClick(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "ctrl-click", Area.ANYWHERE, Store.GENERAL, "Keep Ctrl and a left click a Ctrl click, not a right click.")
}
