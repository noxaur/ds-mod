package io.github.noxaur.dsmod.core.game

/** @keep Each place is taken from a capture: prefer whichever of the tab list row and the sidebar place arrives
 * first. A dungeon, the Dungeon Hub and a part of an area are told by the sidebar's `⏣` line */
enum class Area(val key: String?, val value: String?, val place: String?, val folder: String?) {
    ANYWHERE(null, null, null, null),
    VILLAGE("Area", "Village", null, "village"),
    CRYPTS("Area", "The Crypts", null, "crypts"),
    END("Area", "The End", null, "end"),

    // @keep The tab list still reads `Area: The End` there
    VOID_SEPULTURE("Area", "The End", "Void Sepulture", "sepulture"),

    DUNGEON_HUB(null, null, "Dungeon Hub", "dungeonhub"),

    /** @keep The sidebar adds the floor, as in `The Catacombs (F1)`, and says so with the new world. The tab list's
     * `Dungeon: Catacombs` row came 1.5 s later in a capture, after the run's first lines */
    CATACOMBS(null, null, "The Catacombs", "dungeon"),
    ;

    val title: String get() = place ?: value ?: "Anywhere"

    val row: String? get() = key?.let { "$it: $value" }

    val line: String? get() = place?.let { "⏣ $it" }

    fun holds(tab: TabState, sidebar: SidebarState) =
        (key == null || tab.info(key) == value) && (place == null || sidebar.place?.startsWith(place) == true)
}
