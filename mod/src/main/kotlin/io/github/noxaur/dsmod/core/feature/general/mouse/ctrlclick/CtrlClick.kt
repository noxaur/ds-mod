package io.github.noxaur.dsmod.core.feature.general.mouse.ctrlclick

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

class CtrlClick(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "ctrl-click", Area.ANYWHERE, Store.GENERAL, "Make Ctrl + left click a Ctrl click, instead of the Mac's right click.")
}
