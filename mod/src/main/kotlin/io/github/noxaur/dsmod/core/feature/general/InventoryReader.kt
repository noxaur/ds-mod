package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.ContainerState.Companion.INVENTORY
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.WorldChanged

/** The part of a HUD [feature] that reads the player's own inventory, which sets [stale] when it may have changed since it was last read. */
class InventoryReader(private val feature: Feature) {
    /** A refresh with nothing new then costs a few compares. */
    var stale = true

    init {
        feature.on<ContainerOpened> { event, _ -> if (event.id == INVENTORY) stale = true }
        // Also run when the feature is switched off, so it reads again when it comes back.
        feature.on<WorldChanged> { _, _ -> stale = true }
    }

    /** Sets [stale] when [event] changed a slot of the inventory that the feature [watches], for its own `SlotChanged` handler. */
    fun changed(event: SlotChanged, watches: (slot: Int) -> Boolean) {
        if (feature.container.id == INVENTORY && watches(event.slot)) stale = true
    }
}
