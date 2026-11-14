package io.github.noxaur.dsmod.core.ui

/**
 * What is selected in an ordered list, by the rules a player knows from a file manager. [anchor] is where a
 * Shift press extends from and [focus] is the end that moves. Every function takes the list as it is now,
 * because a list such as chat grows and drops items while a selection stands.
 */
data class Selection<T>(val items: Set<T> = emptySet(), val anchor: T? = null, val focus: T? = null) {
    /** The selected items in the list's order. */
    fun ordered(all: List<T>) = all.filter { it in items }

    /** Nothing selected, with the anchor kept for a later Shift press. */
    fun cleared() = Selection(emptySet(), anchor, focus)

    fun all(all: List<T>) = Selection(all.toSet(), all.firstOrNull(), all.lastOrNull())

    /** Moves the focus [by] items and selects from the anchor to it, as Shift with an arrow key does. */
    fun step(all: List<T>, by: Int): Selection<T> {
        val pivot = anchor?.takeIf { it in all } ?: return this
        val from = all.indexOf(focus ?: pivot).takeIf { it >= 0 } ?: return this
        val to = all[(from + by).coerceIn(all.indices)]
        return Selection(range(all, pivot, to), pivot, to)
    }

    /**
     * The button went down on [item]. [extend] is Shift: the range runs from the anchor. [toggle] is Ctrl: what
     * was selected stays, and a press on a selected item takes it out. With neither, a press on an item of a
     * selection of several keeps them all until the mouse leaves that item, so their menu can be opened again.
     */
    fun press(all: List<T>, item: T, extend: Boolean, toggle: Boolean) = Drag(
        before = this,
        pivot = if (extend) anchor?.takeIf { it in all } ?: item else item,
        pressed = item,
        base = if (toggle) items else emptySet(),
        remove = toggle && !extend && item in items,
        keep = !extend && !toggle && items.size > 1 && item in items,
    )

    /** A press that is still held. It is worked out from the selection at the press, so backing off undoes it. */
    class Drag<T> internal constructor(
        private val before: Selection<T>,
        private val pivot: T,
        private val pressed: T,
        private val base: Set<T>,
        private val remove: Boolean,
        private val keep: Boolean,
    ) {
        /** The selection with the mouse over [item]. */
        fun over(all: List<T>, item: T): Selection<T> {
            if (keep && item == pressed) return before
            val range = range(all, pivot, item)
            return Selection(if (remove) base - range else base + range, pivot, item)
        }
    }

    private companion object {
        fun <T> range(all: List<T>, a: T, b: T): Set<T> {
            val from = all.indexOf(a)
            val to = all.indexOf(b)
            if (from < 0 || to < 0) return setOfNotNull(b.takeIf { to >= 0 })
            return all.subList(minOf(from, to), maxOf(from, to) + 1).toSet()
        }
    }
}
