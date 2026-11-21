package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

class CtrlClick(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "ctrl-click", Area.ANYWHERE, Store.GENERAL, "Keep Ctrl and a left click a Ctrl click, not a right click.")
}
