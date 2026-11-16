package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.ContainerState.Companion.INVENTORY
import io.github.noxaur.dsmod.core.game.WorldChanged
import kotlin.reflect.KMutableProperty0

/** A HUD feature that reads the player's own inventory, and sets [stale] when it may have changed since it was last read. */
abstract class InventoryReader(id: String, toggle: KMutableProperty0<Boolean>) : Feature(id, toggle, Area.ANYWHERE) {
    /** A refresh with nothing new then costs a few compares. */
    protected var stale = true

    init {
        on<ContainerOpened> { event, _ -> if (event.id == INVENTORY) stale = true }
        // Also run when the feature is switched off, so it reads again when it comes back.
        on<WorldChanged> { _, _ -> stale = true }
    }
}
