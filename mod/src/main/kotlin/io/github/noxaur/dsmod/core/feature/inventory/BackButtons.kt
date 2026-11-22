package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

class BackButtons(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "back-buttons", Area.ANYWHERE, Store.BETTER_UI, "Add Go Back to menus without one, by resending your last command.")
}
