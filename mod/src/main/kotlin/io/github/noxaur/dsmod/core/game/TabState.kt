package io.github.noxaur.dsmod.core.game

import io.github.noxaur.dsmod.core.capture.PlayerNames

/** What the tab list says, read once per [TabList]. A feature never reads the `Area` row: it names its [Area], which decides. An info row is one that reads `Key: value`. */
class TabState {
    private val info = HashMap<String, String>()
    private var rows = emptyList<ServerText>()
    private var names: Set<String>? = null

    /** The players the tab list shows, read when asked and not per [TabList], since few callers want them. */
    val players: Set<String>
        get() = names ?: rows.map { it.plain }.let { plain ->
            PlayerNames.inTabList(plain).flatMapIndexedTo(HashSet()) { i, ranges -> ranges.map(plain[i]::substring) }
        }.also { names = it }

    /** The value of the info row `key: value`, or null when the tab list has no such row. */
    fun info(key: String): String? = info[key]

    fun update(rows: List<ServerText>) {
        this.rows = rows
        names = null
        info.clear()
        for (row in rows) info.putInfo(row.plain)
    }
}
