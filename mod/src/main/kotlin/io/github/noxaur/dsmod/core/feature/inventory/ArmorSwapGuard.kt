package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

/**
 * Stops a right click from swapping the armor in the player's hand onto them, unless they sneak. It reads
 * nothing from the server, so all of it is `ArmorSwapGuards` in the edge.
 */
class ArmorSwapGuard(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "armor-swap-guard", Area.ANYWHERE, Store.INVENTORY, "Stop a right click from swapping on armor, unless you sneak.", default = false)
}
