package io.github.noxaur.dragen.core.game

import io.github.noxaur.dragen.core.capture.PlayerNames

class TabState {
    private val info = HashMap<String, String>()
    private var rows = emptyList<ServerText>()
    private var names: Set<String>? = null

    val players: Set<String>
        get() = names ?: rows.map { it.plain }.let { plain ->
            PlayerNames.inTabList(plain).flatMapIndexedTo(HashSet()) { i, ranges -> ranges.map(plain[i]::substring) }
        }.also { names = it }

    fun info(key: String): String? = info[key]

    fun update(rows: List<ServerText>) {
        this.rows = rows
        names = null
        info.clear()
        for (row in rows) info.putInfo(row.plain)
    }
}
