package io.github.noxaur.dsmod.core.game

/**
 * What the sidebar says, read once per [Sidebar]. It keeps every line as the server styled it, so a layer can draw
 * the whole sidebar again, and reads out the two things features ask for: the [place] and the `Key: value` lines.
 */
class SidebarState {
    private val info = HashMap<String, String>()

    var title = ServerText()
        private set

    /** Every line from the top, blank ones too. */
    var lines: List<ServerText> = emptyList()
        private set

    /**
     * The part of the area the player stands in, from the line the server marks with `⏣`, such as `Dragon's Den`
     * or `Dungeon Hub`. A dungeon adds its floor, as in `The Catacombs (F1)`. Null before the sidebar says.
     */
    var place: String? = null
        private set

    /** The value of the line `key: value`, such as `Purse`, or null when the sidebar has no such line. */
    fun info(key: String): String? = info[key]

    fun update(event: Sidebar) {
        title = event.title
        lines = event.lines
        place = null
        info.clear()
        for (text in event.lines) {
            val line = text.plain.trim()
            if (line.startsWith(PLACE_MARK)) {
                if (place == null) place = line.substring(PLACE_MARK.length).trim().ifEmpty { null }
                continue
            }
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val value = line.substring(colon + 1).trim()
            if (value.isNotEmpty()) info.putIfAbsent(line.substring(0, colon).trim(), value)
        }
    }

    private companion object {
        const val PLACE_MARK = "⏣"
    }
}
