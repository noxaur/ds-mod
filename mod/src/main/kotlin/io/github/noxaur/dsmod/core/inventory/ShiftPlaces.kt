package io.github.noxaur.dsmod.core.inventory

data class ShiftSlot(
    val index: Int,
    val count: Int,
    val limit: Int,
    val same: Boolean,
    val acceptsEmpty: Boolean,
    val locked: Boolean,
)

fun shiftPlaces(moving: Int, slots: List<ShiftSlot>, skipLocked: Boolean = true, stackable: Boolean = true): List<Pair<Int, Int>> {
    if (moving <= 0) return emptyList()
    val taken = IntArray(slots.size)
    val placed = mutableListOf<Pair<Int, Int>>()
    var left = moving
    if (stackable) left = fill(slots, taken, placed, left, skipLocked, empty = false)
    fill(slots, taken, placed, left, skipLocked, empty = true)
    return placed
}

private fun fill(slots: List<ShiftSlot>, taken: IntArray, placed: MutableList<Pair<Int, Int>>, moving: Int, skipLocked: Boolean, empty: Boolean): Int {
    var left = moving
    for ((i, slot) in slots.withIndex()) {
        if (left == 0) break
        if (skipLocked && slot.locked) continue
        val have = slot.count + taken[i]
        val room = when {
            !empty -> if (slot.same && have in 1 until slot.limit) slot.limit - have else 0
            have == 0 && slot.acceptsEmpty -> slot.limit
            else -> 0
        }
        if (room <= 0) continue
        val n = minOf(left, room)
        taken[i] += n
        placed += slot.index to n
        left -= n
    }
    return left
}
