package io.github.noxaur.dsmod.core.ui

/** A line of a [NavTree]: a section, or its subsection [sub] when that is 0 or more. */
data class NavRow(val section: Int, val sub: Int = -1) {
    val isSection get() = sub < 0
}

/**
 * The sidebar of a settings screen: sections, each with [subs] named subsections that it can show beneath it.
 * `controls.md` has the navigation tree's conventions, and this holds their rules.
 *
 * A section shows its subsections when the player opened it with its arrow, or when it is [selected] and the
 * player has not closed it with its arrow. Both marks last as long as the tree, across changes of section.
 * While [searching], no section shows its subsections. [cursor] is the line the keyboard is on.
 */
class NavTree(private val subs: List<Int>) {
    private val opened = BooleanArray(subs.size)
    private val closed = BooleanArray(subs.size)

    /** The section the pane shows, or -1 for none. */
    var selected = 0
        private set

    /** The subsection of the selected section that the pane is showing, which the sidebar marks. */
    var current = 0

    var searching = false
        set(value) {
            field = value
            refresh()
        }

    var cursor = NavRow(0)
        private set

    /** The lines the sidebar shows, from the top. */
    var rows: List<NavRow> = emptyList()
        private set

    init {
        refresh()
    }

    fun expandable(section: Int) = subs[section] > 0

    fun shows(section: Int) = !searching && expandable(section) && (opened[section] || section == selected && !closed[section])

    /** Whether any section shows its subsections, which makes the sidebar's button Collapse All. */
    val anyShown get() = subs.indices.any(::shows)

    /** Whether the sidebar marks [row] as the one the pane is on. */
    fun marked(row: NavRow) = !searching && row.section == selected && (row.isSection || row.sub == current)

    /**
     * What the narrator says for [row], named from [names] and [subNames]: its kind, whether it shows its subsections
     * or, during a search, its count of [counts], and whether the sidebar marks it.
     */
    fun narration(row: NavRow, names: List<String>, subNames: List<List<String>>, counts: IntArray?): String {
        if (!row.isSection) return Narration.join(subNames[row.section][row.sub], "subsection of ${names[row.section]}", "current".takeIf { marked(row) })
        val state = when {
            counts != null -> counts[row.section].let { if (it == 0) "no matches" else if (it == 1) "1 match" else "$it matches" }
            expandable(row.section) -> if (shows(row.section)) "expanded" else "collapsed"
            else -> null
        }
        return Narration.join(names[row.section], "section", state, "selected".takeIf { marked(row) })
    }

    /** The arrow beside [section]: shows its subsections, or hides them for as long as the player leaves it closed. */
    fun toggle(section: Int) {
        if (!expandable(section)) return
        val shown = shows(section)
        opened[section] = !shown
        closed[section] = shown
        refresh()
    }

    /** Opens [section], with the pane on its first subsection. Selecting a section forgets that it was closed. */
    fun select(section: Int) = open(section, NavRow(section))

    /** Opens [section] with the pane on its subsection [sub]. */
    fun jump(section: Int, sub: Int) = open(section, NavRow(section, sub))

    /** Selects no section, for a blank tab. */
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

    /** Opens every section that has subsections. */
    fun expandAll() {
        for (i in subs.indices) {
            opened[i] = expandable(i)
            closed[i] = false
        }
        refresh()
    }

    /** Closes every section, the selected one too. */
    fun collapseAll() {
        for (i in subs.indices) {
            opened[i] = false
            closed[i] = expandable(i)
        }
        refresh()
    }

    /** Moves the cursor [by] lines, stopping at the first and last. */
    fun move(by: Int) {
        val at = rows.indexOf(cursor).coerceAtLeast(0)
        cursor = rows[(at + by).coerceIn(rows.indices)]
    }

    fun moveTo(row: NavRow) {
        if (row in rows) cursor = row
    }

    /** Right: opens the section under the cursor, or steps into its first subsection when it is open. */
    fun right() {
        val row = cursor
        if (!row.isSection || !expandable(row.section) || searching) return
        if (shows(row.section)) cursor = NavRow(row.section, 0) else toggle(row.section)
    }

    /** Left: closes the section under the cursor, or steps out of a subsection to its section. */
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
        // A subsection that was hidden hands the cursor to its section.
        if (cursor !in next) cursor = NavRow(cursor.section.coerceIn(subs.indices))
    }

    companion object {
        /** How far below the pane's top a heading may sit and still count as the one the pane is on. */
        const val REACH = 27

        /**
         * Which subsection the pane is on, scrolled to [scroll] of [max]: the last heading whose top in [tops] is
         * within [REACH] of the pane's top, or the last heading once the pane is at its end.
         */
        fun current(tops: IntArray, scroll: Int, max: Int): Int {
            if (tops.isEmpty()) return 0
            if (max > 0 && scroll >= max) return tops.lastIndex
            var at = 0
            for (i in tops.indices) if (tops[i] - scroll < REACH) at = i
            return at
        }
    }
}
