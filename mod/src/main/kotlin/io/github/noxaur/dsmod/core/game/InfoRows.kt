package io.github.noxaur.dsmod.core.game

class InfoRows(private val read: (String) -> String?) {
    fun info(key: String): String? {
        require(key !in AREA_KEYS) { "A feature never reads the `$key` row to learn where the player is. Pass its `Area` to `Feature(..)`." }
        return read(key)
    }

    companion object {
        val AREA_KEYS: Set<String> = Area.entries.mapNotNullTo(hashSetOf("Dungeon")) { it.key }
    }
}

internal fun MutableMap<String, String>.putInfo(line: String) {
    val colon = line.indexOf(':')
    if (colon <= 0) return
    val value = line.substring(colon + 1).trim()
    if (value.isNotEmpty()) putIfAbsent(line.substring(0, colon).trim(), value)
}
