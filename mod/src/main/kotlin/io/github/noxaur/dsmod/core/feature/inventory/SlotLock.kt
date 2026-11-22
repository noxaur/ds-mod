package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.inventory.SlotLocks

class SlotLock(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "slot-lock", Area.ANYWHERE, Store.INVENTORY, "Lock inventory slots and items so a misclick cannot move them.")

    val slotLockKey = feature.keys("slotLockKey", "Slot Key", "Pick the key or mouse button that locks the slot under the mouse.", KEY_L)
    val itemLockKey = feature.keys("itemLockKey", "Item Key", "Pick the key that locks an item that does not stack.", KEY_K)

    private val kept = feature.saved(SlotLocks())

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
