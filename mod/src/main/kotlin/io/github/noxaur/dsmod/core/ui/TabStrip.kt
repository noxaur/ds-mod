package io.github.noxaur.dsmod.core.ui

/**
 * Where a browser's row of tabs puts each tab, in GUI pixels from the strip's left edge. The tabs share [room] with
 * the New Tab button after them, each [MAX] wide and shrinking to [MIN]. Past that the tabs scroll by [offset]
 * within [view], the button stays after the view, and [reveal] keeps a tab in sight.
 */
class TabStrip {
    var width = MAX
        private set

    /** How wide the part of the strip that shows tabs is. */
    var view = 0
        private set

    var offset = 0
        private set

    private var count = 0

    private val step get() = width + GAP
    private val content get() = (count * step - GAP).coerceAtLeast(0)
    private val max get() = (content - view).coerceAtLeast(0)

    val overflows get() = content > view

    /** Lays out [count] tabs in [room], keeping [selected] in sight. */
    fun layout(count: Int, room: Int, selected: Int) {
        this.count = count
        view = (room - NEW - GAP).coerceAtLeast(0)
        width = if (count == 0) MAX else ((view + GAP) / count - GAP).coerceIn(MIN, MAX)
        offset = offset.coerceIn(0, max)
        reveal(selected)
    }

    /** Where tab [index] starts, scrolled. */
    fun x(index: Int) = index * step - offset

    /** Where the New Tab button starts: after the last tab, or after the view once the tabs scroll. */
    val newX get() = content.coerceAtMost(view) + GAP

    /** The tab at [x], or -1 for none. */
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

    /**
     * Where a tab dragged from [from] belongs with the mouse at [x]: it passes a neighbor once the mouse crosses the
     * neighbor's middle, as a browser's tab does.
     */
    fun drop(x: Int, from: Int): Int {
        val at = x + offset
        for (i in 0 until count) if (at < i * step + width / 2) return if (i > from) i - 1 else i
        return count - 1
    }

    /** Where a dragged tab grabbed [grab] from its left edge is drawn, with the mouse at [x], kept within the strip. */
    fun dragX(x: Int, grab: Int) = (x - grab).coerceIn(-offset, content - width - offset)

    companion object {
        const val MAX = 92
        const val MIN = 44
        const val GAP = 1

        /** The New Tab button's width. */
        const val NEW = 15
    }
}
