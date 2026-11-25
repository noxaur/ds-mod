package io.github.noxaur.dragen.core.ui

data class Place(val section: Int, val query: String = "") {
    val searching get() = query.isNotBlank()

    companion object {
        val BLANK = Place(-1)
    }
}

class SettingsTab(place: Place) {
    private class Visit(val place: Place, val scroll: Int, val current: Int)

    var place = place
        private set
    var scroll = 0
    var current = 0

    var jump = -1

    private val back = ArrayList<Visit>()
    private val forward = ArrayList<Visit>()

    fun title(names: List<String>) = when {
        place.searching -> place.query.trim()
        place.section < 0 -> "New Tab"
        else -> names[place.section]
    }

    fun go(section: Int) {
        val to = Place(section)
        if (to != place) visit(to)
        toTop()
    }

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

    fun narration(index: Int, names: List<String>) = list[index].let { tab ->
        Narration.join(tab.title(names), if (tab.place.searching) "search tab" else "tab", "selected".takeIf { index == selected }, Narration.of(index, size))
    }

    fun select(index: Int) {
        selected = index.coerceIn(list.indices)
    }

    fun cycle(by: Int) {
        selected = Math.floorMod(selected + by, list.size)
    }

    fun number(digit: Int) {
        if (digit == 9) selected = list.lastIndex else if (digit in 1..list.size) selected = digit - 1
    }

    fun open(place: Place, jump: Int = -1) {
        list.add(selected + 1, SettingsTab(place).also { it.jump = jump })
    }

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

    fun reopen(): Boolean {
        val last = closed.removeLastOrNull() ?: return false
        val at = last.index.coerceAtMost(list.size)
        list.add(at, last.tab)
        selected = at
        return true
    }

    fun move(from: Int, to: Int) {
        if (from == to || from !in list.indices || to !in list.indices) return
        val keep = list[selected]
        list.add(to, list.removeAt(from))
        selected = list.indexOf(keep)
    }
}
