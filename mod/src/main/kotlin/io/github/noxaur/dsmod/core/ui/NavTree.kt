package io.github.noxaur.dsmod.core.ui

data class NavRow(val section: Int, val sub: Int = -1) {
    val isSection get() = sub < 0
}

class NavTree(private val subs: List<Int>) {
    private val opened = BooleanArray(subs.size)
    private val closed = BooleanArray(subs.size)

    var selected = 0
        private set

    var current = 0

    var searching = false
        set(value) {
            field = value
            refresh()
        }

    var cursor = NavRow(0)
        private set

    var rows: List<NavRow> = emptyList()
        private set

    init {
        refresh()
    }

    fun expandable(section: Int) = subs[section] > 0

    fun shows(section: Int) = !searching && expandable(section) && (opened[section] || section == selected && !closed[section])

    val anyShown get() = subs.indices.any(::shows)

    fun marked(row: NavRow) = !searching && row.section == selected && (row.isSection || row.sub == current)

    fun narration(row: NavRow, names: List<String>, subNames: List<List<String>>, counts: IntArray?): String {
        if (!row.isSection) return Narration.join(subNames[row.section][row.sub], "subsection of ${names[row.section]}", "current".takeIf { marked(row) })
        val state = when {
            counts != null -> counts[row.section].let { if (it == 0) "no matches" else if (it == 1) "1 match" else "$it matches" }
            expandable(row.section) -> if (shows(row.section)) "expanded" else "collapsed"
            else -> null
        }
        return Narration.join(names[row.section], "section", state, "selected".takeIf { marked(row) })
    }

    fun toggle(section: Int) {
        if (!expandable(section)) return
        val shown = shows(section)
        opened[section] = !shown
        closed[section] = shown
        refresh()
    }

    fun select(section: Int) = open(section, NavRow(section))

    fun jump(section: Int, sub: Int) = open(section, NavRow(section, sub))

    fun deselect() {
        selected = -1
        refresh()
    }

    private fun open(section: Int, row: NavRow) {
        selected = section
        current = row.sub.coerceAtLeast(0)
        closed[section] = false
        cursor = row
        refresh()
    }

    fun expandAll() {
        for (i in subs.indices) {
            opened[i] = expandable(i)
            closed[i] = false
        }
        refresh()
    }

    fun collapseAll() {
        for (i in subs.indices) {
            opened[i] = false
            closed[i] = expandable(i)
        }
        refresh()
    }

    fun move(by: Int) {
        val at = rows.indexOf(cursor).coerceAtLeast(0)
        cursor = rows[(at + by).coerceIn(rows.indices)]
    }

    fun moveTo(row: NavRow) {
        if (row in rows) cursor = row
    }

    fun right() {
        val row = cursor
        if (!row.isSection || !expandable(row.section) || searching) return
        if (shows(row.section)) cursor = NavRow(row.section, 0) else toggle(row.section)
    }

    fun left() {
        val row = cursor
        if (!row.isSection) cursor = NavRow(row.section) else if (shows(row.section)) toggle(row.section)
    }

    private fun refresh() {
        val next = ArrayList<NavRow>()
        for (i in subs.indices) {
            next += NavRow(i)
            if (shows(i)) for (j in 0 until subs[i]) next += NavRow(i, j)
        }
        rows = next
        if (cursor !in next) cursor = NavRow(cursor.section.coerceIn(subs.indices))
    }

    companion object {
        const val REACH = 27

        fun current(tops: IntArray, scroll: Int, max: Int): Int {
            if (tops.isEmpty()) return 0
            if (max > 0 && scroll >= max) return tops.lastIndex
            var at = 0
            for (i in tops.indices) if (tops[i] - scroll < REACH) at = i
            return at
        }
    }
}
