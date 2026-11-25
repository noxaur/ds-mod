package io.github.noxaur.dragen.core.ui

class TabStrip {
    var width = MAX
        private set

    var view = 0
        private set

    var offset = 0
        private set

    private var count = 0

    private val step get() = width + GAP
    private val content get() = (count * step - GAP).coerceAtLeast(0)
    private val max get() = (content - view).coerceAtLeast(0)

    val overflows get() = content > view

    fun layout(count: Int, room: Int, selected: Int) {
        this.count = count
        view = (room - NEW - GAP).coerceAtLeast(0)
        width = if (count == 0) MAX else ((view + GAP) / count - GAP).coerceIn(MIN, MAX)
        offset = offset.coerceIn(0, max)
        reveal(selected)
    }

    fun x(index: Int) = index * step - offset

    val newX get() = content.coerceAtMost(view) + GAP

    fun at(x: Int): Int {
        if (x < 0 || x >= view) return -1
        val at = x + offset
        val index = at / step
        return if (index < count && at % step < width) index else -1
    }

    fun scroll(by: Int) {
        offset = (offset + by).coerceIn(0, max)
    }

    fun reveal(index: Int) {
        if (index !in 0 until count) return
        val left = index * step
        if (left < offset) offset = left else if (left + width > offset + view) offset = left + width - view
        offset = offset.coerceIn(0, max)
    }

    fun drop(x: Int, from: Int): Int {
        val at = x + offset
        for (i in 0 until count) if (at < i * step + width / 2) return if (i > from) i - 1 else i
        return count - 1
    }

    fun dragX(x: Int, grab: Int) = (x - grab).coerceIn(-offset, content - width - offset)

    companion object {
        const val MAX = 92
        const val MIN = 44
        const val GAP = 1

        const val NEW = 15
    }
}
