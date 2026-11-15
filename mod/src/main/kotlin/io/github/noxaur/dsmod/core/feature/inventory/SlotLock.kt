package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.inventory.SlotLocks
import kotlin.reflect.KMutableProperty0

/**
 * Keeps the player's locked slots between sessions. It reads nothing from the server, so the clicks it refuses
 * are `SlotGuards` in the edge, with the rules in `core.inventory`.
 */
class SlotLock(toggle: KMutableProperty0<Boolean>) : Feature("slot-lock", toggle, Area.ANYWHERE) {
    private val kept = saved(SlotLocks())

    var locks: SlotLocks
        get() = kept.value
        set(value) {
            kept.value = value
        }
}
