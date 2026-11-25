package io.github.noxaur.dsmod.core.feature.inventory.protection.armorswapguard

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

class ArmorSwapGuard(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "armor-swap-guard", Area.ANYWHERE, Store.INVENTORY, "Stop a right click from swapping your armor, unless you sneak.", default = false)
}
