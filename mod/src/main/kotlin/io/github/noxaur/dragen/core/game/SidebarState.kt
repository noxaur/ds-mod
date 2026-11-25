package io.github.noxaur.dragen.core.game

class SidebarState {
    private val info = HashMap<String, String>()

    /** @keep The line the server marks with `⏣`, such as `Dragon's Den` or `Dungeon Hub`. A dungeon adds its floor, as in
     * `The Catacombs (F1)` */
    var place: String? = null
        private set

    fun info(key: String): String? = info[key]

    fun update(event: Sidebar) {
        place = null
        info.clear()
        for (text in event.lines) {
            val line = text.plain.trim()
            if (line.startsWith(PLACE_MARK)) {
                if (place == null) place = line.substring(PLACE_MARK.length).trim().ifEmpty { null }
                continue
            }
            info.putInfo(line)
        }
    }

    private companion object {
        const val PLACE_MARK = "⏣"
    }
}
