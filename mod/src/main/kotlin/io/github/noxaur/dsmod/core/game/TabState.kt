package io.github.noxaur.dsmod.core.game

/** What the tab list says, read once per [TabList]. A feature never reads the `Area` row: it names its [Area] and [isIn] decides. An info row is one that reads `Key: value`. */
class TabState {
    private val info = HashMap<String, String>()

    /** Whether the server says the player is in [area]. Before it names any area, the player is in none. */
    fun isIn(area: Area) = area.key == null || info[area.key] == area.value

    /** The value of the info row `key: value`, or null when the tab list has no such row. */
    fun info(key: String): String? = info[key]

    fun update(rows: List<ServerText>) {
        info.clear()
        for (row in rows) {
            val line = row.plain
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val value = line.substring(colon + 1).trim()
            if (value.isNotEmpty()) info.putIfAbsent(line.substring(0, colon).trim(), value)
        }
    }
}
