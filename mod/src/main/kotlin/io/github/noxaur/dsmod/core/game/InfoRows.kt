package io.github.noxaur.dsmod.core.game

/**
 * The `Key: value` rows of the tab list or the sidebar, which is all of either that a feature sees. The place line and
 * the rows that tell the player's [Area] stay out of reach, because the feature names its `Area` and `Game` decides.
 */
class InfoRows(private val read: (String) -> String?) {
    /** The value of the row `key: value`, or null when there is no such row. */
    fun info(key: String): String? {
        require(key !in AREA_KEYS) { "A feature never reads the `$key` row to learn where the player is. Pass its `Area` to `Feature(..)`." }
        return read(key)
    }

    companion object {
        val AREA_KEYS: Set<String> = Area.entries.mapNotNullTo(hashSetOf("Dungeon")) { it.key }
    }
}
