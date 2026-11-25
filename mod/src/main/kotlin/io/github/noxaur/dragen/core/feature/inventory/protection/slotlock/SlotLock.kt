package io.github.noxaur.dragen.core.feature.inventory.protection.slotlock

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area
import io.github.noxaur.dragen.core.inventory.SlotLocks

class SlotLock(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "slot-lock", Area.ANYWHERE, Store.INVENTORY, "Lock slots and items so a misclick can't move or drop them.")

    val slotLockKey = feature.keys("slotLockKey", "Slot Key", "Pick the key or mouse button that locks the slot under the mouse.", KEY_L, clicks = false)
    val itemLockKey = feature.keys("itemLockKey", "Item Key", "Pick the key that locks an item that doesn't stack.", KEY_K, clicks = false)

    private val kept = feature.profiled(SlotLocks())

    var locks: SlotLocks
        get() = kept.value
        set(value) {
            kept.value = value
        }

    private companion object {
        const val KEY_K = 75
        const val KEY_L = 76
    }
}
