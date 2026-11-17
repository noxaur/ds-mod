package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.ContainerState.Companion.INVENTORY
import io.github.noxaur.dsmod.core.game.WorldChanged

/** A HUD feature that reads the player's own inventory, and sets [stale] when it may have changed since it was last read. */
abstract class InventoryReader(config: DsModConfig, id: String, description: String, default: Boolean = true, formerly: List<String> = emptyList()) :
    Feature(config, id, Area.ANYWHERE, Store.GENERAL, description, default, formerly) {
    /** A refresh with nothing new then costs a few compares. */
    protected var stale = true

    init {
        on<ContainerOpened> { event, _ -> if (event.id == INVENTORY) stale = true }
        // Also run when the feature is switched off, so it reads again when it comes back.
        on<WorldChanged> { _, _ -> stale = true }
    }
}
