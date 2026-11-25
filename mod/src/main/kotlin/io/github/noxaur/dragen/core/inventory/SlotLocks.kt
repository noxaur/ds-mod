package io.github.noxaur.dragen.core.inventory

import kotlinx.serialization.Serializable

/** @keep A slot is its index in the inventory, never in a menu: 0 to 8 is the hotbar, 9 to 35 the rows above it, 36
 * to 39 the armor and 40 the off hand. items are the IDs the server gives each item that does not stack, and a
 * lock on one follows the item from slot to slot */
@Serializable
data class SlotLocks(val locked: Set<Int> = emptySet(), val links: Map<Int, Int> = emptyMap(), val items: Set<String> = emptySet()) {
    fun item(id: String, locked: Boolean): SlotLocks = copy(items = if (locked) items + id else items - id)

    fun partner(slot: Int): Int? = links[slot] ?: links.entries.firstOrNull { it.value == slot }?.key

    fun toggle(slot: Int): SlotLocks =
        if (slot in locked) copy(locked = locked - slot, links = without(slot)) else copy(locked = locked + slot)

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
    }
}
