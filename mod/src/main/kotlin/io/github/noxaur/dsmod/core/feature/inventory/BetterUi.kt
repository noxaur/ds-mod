package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

class BetterUi(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "better-ui", Area.ANYWHERE, Store.BETTER_UI, "Turn the controls the mod adds to menus on or off at once.", formerly = listOf("enabled"))
}
