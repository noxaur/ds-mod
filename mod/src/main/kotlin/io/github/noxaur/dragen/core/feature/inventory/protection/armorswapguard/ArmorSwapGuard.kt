package io.github.noxaur.dragen.core.feature.inventory.protection.armorswapguard

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area

class ArmorSwapGuard(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "armor-swap-guard", Area.ANYWHERE, Store.INVENTORY, "Stop a right click from swapping on armor, unless you sneak.", default = false)
}
