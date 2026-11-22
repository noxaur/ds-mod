package io.github.noxaur.dsmod.core.ui

data class Selection<T>(val items: Set<T> = emptySet(), val anchor: T? = null, val focus: T? = null) {
    fun ordered(all: List<T>) = all.filter { it in items }

    fun cleared() = Selection(emptySet(), anchor, focus)

    fun all(all: List<T>) = Selection(all.toSet(), all.firstOrNull(), all.lastOrNull())

    fun step(all: List<T>, by: Int): Selection<T> {
        val pivot = anchor?.takeIf { it in all } ?: return this
        val from = all.indexOf(focus ?: pivot).takeIf { it >= 0 } ?: return this
        val to = all[(from + by).coerceIn(all.indices)]
        return Selection(range(all, pivot, to), pivot, to)
    }

    fun press(all: List<T>, item: T, extend: Boolean, toggle: Boolean) = Drag(
        before = this,
        pivot = if (extend) anchor?.takeIf { it in all } ?: item else item,
        pressed = item,
        base = if (toggle) items else emptySet(),
        remove = toggle && !extend && item in items,
        keep = !extend && !toggle && items.size > 1 && item in items,
    )

    class Drag<T> internal constructor(
        private val before: Selection<T>,
        private val pivot: T,
        private val pressed: T,
        private val base: Set<T>,
        private val remove: Boolean,
        private val keep: Boolean,
    ) {
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
