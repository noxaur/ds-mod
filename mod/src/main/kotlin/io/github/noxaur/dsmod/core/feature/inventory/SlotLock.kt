package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.inventory.SlotLocks

/**
 * Keeps the player's locked slots between sessions. It reads nothing from the server, so the clicks it refuses
 * are `SlotGuards` in the edge, with the rules in `core.inventory`.
 */
class SlotLock(config: DsModConfig) :
    Feature(config, "slot-lock", Area.ANYWHERE, Store.INVENTORY, "Lock inventory slots and items so a misclick cannot move them.") {
    /** A key's GLFW code, a mouse button from 0 to 9, or -1 for none, as `KeyBind` holds it. */
    val slotLockKey = keys("slotLockKey", "Slot Key", "Pick the key or mouse button that locks the slot under the mouse.", KEY_L)
    val itemLockKey = keys("itemLockKey", "Item Key", "Pick the key that locks an item that does not stack.", KEY_K)

    private val kept = saved(SlotLocks())

    var locks: SlotLocks
        get() = kept.value
        set(value) {
            kept.value = value
        }

    private companion object {
        /** GLFW's codes for the K and L keys. */
        const val KEY_K = 75
        const val KEY_L = 76
    }
}
