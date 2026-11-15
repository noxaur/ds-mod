package io.github.noxaur.dsmod.core.inventory

import kotlinx.serialization.Serializable

/**
 * The slots of the player's own inventory that refuse a click, and the pairs that trade places on a Shift click.
 * A slot is its index in the inventory, never in a menu: 0 to 8 is the hotbar, 9 to 35 the rows above it, 36 to
 * 39 the armor and 40 the off hand. A lock belongs to the slot, so whatever the player puts there is kept.
 * [links] holds each pair once, from its hotbar slot to its slot above. [items] are the IDs the server gives each
 * item that does not stack, and a lock on one follows the item from slot to slot.
 */
@Serializable
data class SlotLocks(val locked: Set<Int> = emptySet(), val links: Map<Int, Int> = emptyMap(), val items: Set<String> = emptySet()) {
    /** Locks the item with this ID wherever it lies in the inventory, or unlocks it. */
    fun toggleItem(id: String): SlotLocks = copy(items = if (id in items) items - id else items + id)

    /** The slot [slot] trades places with, or null. */
    fun partner(slot: Int): Int? = links[slot] ?: links.entries.firstOrNull { it.value == slot }?.key

    /** Locks [slot], or unlocks it and ends its link. */
    fun toggle(slot: Int): SlotLocks =
        if (slot in locked) copy(locked = locked - slot, links = without(slot)) else copy(locked = locked + slot)

    /**
     * Links a hotbar slot with a slot of the rows above and locks both, or ends the link when it is the one they
     * already have. A slot has one partner, so a new link replaces an old one. Null when the two cannot be linked.
     */
    fun link(a: Int, b: Int): SlotLocks? {
        val hotbar = minOf(a, b)
        val above = maxOf(a, b)
        if (hotbar !in HOTBAR || above !in ABOVE) return null
        if (links[hotbar] == above) return copy(links = links - hotbar)
        return copy(locked = locked + hotbar + above, links = links.filter { (h, m) -> h != hotbar && m != above } + (hotbar to above))
    }

    private fun without(slot: Int) = links.filter { (hotbar, above) -> hotbar != slot && above != slot }

    companion object {
        val HOTBAR = 0..8
        val ABOVE = 9..35
        const val OFF_HAND = 40
    }
}
