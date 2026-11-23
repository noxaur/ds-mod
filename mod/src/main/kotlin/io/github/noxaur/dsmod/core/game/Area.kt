package io.github.noxaur.dsmod.core.game

/** @keep The sidebar's `⏣` line tells a dungeon, the Dungeon Hub and a part of an area. */
enum class Area(val key: String?, val value: String?, val place: String?) {
    ANYWHERE(null, null, null),
    VILLAGE("Area", "Village", null),
    CRYPTS("Area", "The Crypts", null),
    END("Area", "The End", null),

    // @keep The tab list still reads `Area: The End` there
    VOID_SEPULTURE("Area", "The End", "Void Sepulture"),

    DUNGEON_HUB(null, null, "Dungeon Hub"),

    /** @keep The sidebar adds the floor, as in `The Catacombs (F1)`, and says so with the new world. The tab list's
     * `Dungeon: Catacombs` row came 1.5 s later in a capture, after the run's first lines */
    CATACOMBS(null, null, "The Catacombs"),
    ;

    val title: String get() = place ?: value ?: "Anywhere"

    fun holds(tab: TabState, sidebar: SidebarState) =
        (key == null || tab.info(key) == value) && (place == null || sidebar.place?.startsWith(place) == true)

    companion object {
        /** @keep The tab list's `Area:` row names the world, but a place such as `Dungeon Hub` has no such row, so only the sidebar names it. */
        fun named(tab: TabState, sidebar: SidebarState): String? = tab.info("Area") ?: sidebar.place
    }
}
