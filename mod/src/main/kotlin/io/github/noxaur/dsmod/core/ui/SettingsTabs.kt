package io.github.noxaur.dsmod.core.ui

/** Where a tab of the settings screen is: a [section], or none when it is -1, or a search for [query] made from there. */
data class Place(val section: Int, val query: String = "") {
    val searching get() = query.isNotBlank()

    companion object {
        val BLANK = Place(-1)
    }
}

/**
 * One tab of the settings screen: its [place], how far its pane is [scroll]ed, the subsection it marks as [current],
 * and the places it was before and after, which the back and forward buttons walk.
 */
class SettingsTab(place: Place) {
    private class Visit(val place: Place, val scroll: Int, val current: Int)

    var place = place
        private set
    var scroll = 0
    var current = 0

    /** The subsection the pane opens scrolled to when the tab next shows, or -1 for none. */
    var jump = -1

    private val back = ArrayList<Visit>()
    private val forward = ArrayList<Visit>()

    /** The tab's name: its search, its section's name from [names], or New Tab for a blank one. */
    fun title(names: List<String>) = when {
        place.searching -> place.query.trim()
        place.section < 0 -> "New Tab"
        else -> names[place.section]
    }

    /** Goes to [section] at its top, keeping where the tab was in its history unless it was already there. */
    fun go(section: Int) {
        val to = Place(section)
        if (to != place) visit(to)
        toTop()
    }

    /**
     * Turns the tab into a search for [query], or back into its section when [query] is blank. Only the place before
     * the search goes into the history, so each key typed is not a step back.
     */
    fun search(query: String) {
        if (query == place.query) return
        val to = Place(place.section, query)
        if (!place.searching && to.searching) visit(to) else place = to
        toTop()
    }

    fun back() = walk(back, forward)

    fun forward() = walk(forward, back)

    private fun toTop() {
        scroll = 0
        current = 0
        jump = -1
    }

    private fun visit(to: Place) {
        back += here()
        forward.clear()
        place = to
    }

    private fun walk(from: ArrayList<Visit>, to: ArrayList<Visit>): Boolean {
        val visit = from.removeLastOrNull() ?: return false
        to += here()
        place = visit.place
        scroll = visit.scroll
        current = visit.current
        jump = -1
        return true
    }

    private fun here() = Visit(place, scroll, current)
}

/**
 * The tabs of the settings screen, as a browser has them: a list with one [selected], each a [SettingsTab] of its
 * own. A tab opened from the sidebar opens after the selected one and in the background, and closing the selected
 * tab selects the one that took its place, or the one before it at the end. The last tab cannot be closed, and a
 * closed tab can be reopened where it was, with its history.
 */
class SettingsTabs(first: Place) {
    private class Closed(val index: Int, val tab: SettingsTab)

    private val list = arrayListOf(SettingsTab(first))
    private val closed = ArrayList<Closed>()

    val tabs: List<SettingsTab> get() = list
    val size get() = list.size

    var selected = 0
        private set

    val current get() = list[selected]

    val canClose get() = list.size > 1

    /** What the narrator says for the tab at [index], named from [names]. */
    fun narration(index: Int, names: List<String>) = list[index].let { tab ->
        Narration.join(tab.title(names), if (tab.place.searching) "search tab" else "tab", "selected".takeIf { index == selected }, Narration.of(index, size))
    }

    fun select(index: Int) {
        selected = index.coerceIn(list.indices)
    }

    /** Selects the tab [by] places along, wrapping past either end, as Ctrl+Tab does. */
    fun cycle(by: Int) {
        selected = Math.floorMod(selected + by, list.size)
    }

    /** Ctrl and a digit: 1 to 8 select that tab when there is one, and 9 the last. */
    fun number(digit: Int) {
        if (digit == 9) selected = list.lastIndex else if (digit in 1..list.size) selected = digit - 1
    }

    /** Opens [place] after the selected tab without selecting it, scrolled to subsection [jump] when that is 0 or more. */
    fun open(place: Place, jump: Int = -1) {
        list.add(selected + 1, SettingsTab(place).also { it.jump = jump })
    }

    /** Opens a blank tab after the selected one and selects it. */
    fun new() {
        open(Place.BLANK)
        selected++
    }

    fun close(index: Int): Boolean {
        if (!canClose || index !in list.indices) return false
        closed += Closed(index, list.removeAt(index))
        if (index < selected || selected == list.size) selected--
        return true
    }

    /** Opens the tab closed last where it was, and selects it. */
    fun reopen(): Boolean {
        val last = closed.removeLastOrNull() ?: return false
        val at = last.index.coerceAtMost(list.size)
        list.add(at, last.tab)
        selected = at
        return true
    }

    /** Moves the tab at [from] to [to], keeping the same tab selected. */
    fun move(from: Int, to: Int) {
        if (from == to || from !in list.indices || to !in list.indices) return
        val keep = list[selected]
        list.add(to, list.removeAt(from))
        selected = list.indexOf(keep)
    }
}
