package io.github.noxaur.dragen.core.feature

import io.github.noxaur.dragen.core.game.ContainerOpened
import io.github.noxaur.dragen.core.game.ContainerState.Companion.INVENTORY
import io.github.noxaur.dragen.core.game.SlotChanged
import io.github.noxaur.dragen.core.game.WorldChanged

class ContainerReader(private val feature: Feature, private val inventory: IntRange? = null) {
    private var stale = true

    init {
        feature.on<ContainerOpened> { event, _ -> if (inventory == null || event.id == INVENTORY) stale = true }
        feature.on<SlotChanged> { event, _ -> if (inventory == null || feature.container.id == INVENTORY && event.slot in inventory) stale = true }
        feature.on<WorldChanged> { _, _ -> stale = true }
    }

    fun changed(): Boolean {
        if (!stale || inventory != null && (feature.container.id != INVENTORY || feature.container.slots.size <= inventory.last)) return false
        stale = false
        return true
    }
}
