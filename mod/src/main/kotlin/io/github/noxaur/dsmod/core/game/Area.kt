package io.github.noxaur.dsmod.core.game

/**
 * Where a feature runs. The tab list's info row [key] reads [value] there, and the sidebar's place starts with
 * [place], each taken from a capture and each null when it does not tell the area. [folder] is the package under
 * `core/feature` whose features all belong to it.
 */
enum class Area(val key: String?, val value: String?, val place: String?, val folder: String?) {
    ANYWHERE(null, null, null, null),
    VILLAGE("Area", "Village", null, "village"),
    CRYPTS("Area", "The Crypts", null, "crypts"),
    END("Area", "The End", null, "end"),

    /** The part of The End around the Voidgloom altar. The tab list still reads `Area: The End` there. */
    VOID_SEPULTURE("Area", "The End", "Void Sepulture", "sepulture"),

    /** Its tab list has no info row, so only the sidebar tells it. */
    DUNGEON_HUB(null, null, "Dungeon Hub", "dungeonhub"),

    /**
     * A dungeon run. The sidebar adds the floor, as in `The Catacombs (F1)`, and says so with the new world. The
     * tab list's `Dungeon: Catacombs` row came 1.5 s later in a capture, after the run's first lines.
     */
    CATACOMBS(null, null, "The Catacombs", "dungeon"),
    ;

    /** What the server calls the place, for the player to pick it by. */
    val title: String get() = place ?: value ?: "Anywhere"

    /** The tab list row that puts the player here, for a test to send, or null when the tab list does not tell. */
    val row: String? get() = key?.let { "$it: $value" }

    /** The sidebar line that puts the player here, for a test to send, or null when the sidebar does not tell. */
    val line: String? get() = place?.let { "⏣ $it" }

    /** Whether what [tab] and [sidebar] say puts the player here. Before they say anything, the player is nowhere. */
    fun holds(tab: TabState, sidebar: SidebarState) =
        (key == null || tab.info(key) == value) && (place == null || sidebar.place?.startsWith(place) == true)
}
