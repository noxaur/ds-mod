package io.github.noxaur.dsmod.core.config

/**
 * The settings the player starred, each by its path in `DsModConfig`, such as `slayer.slayerTracker`. The settings
 * show them in the order they are declared, so a starred setting sits where the player would look for it in its
 * own category, and not in the order they were starred. These are the rules of any settings screen, so the one that
 * replaces MoulConfig's follows them too.
 */
object Favorites {
    /** Starred before the player stars anything: the trackers and alerts DragSim Utils stars too. */
    val DEFAULT = listOf("loot.lootAlerts", "end.zealotTracker", "slayer.slayerTracker", "slayer.slayerBossAlert")

    /** [starred] with [path] starred, or no longer starred if it was. */
    fun toggle(starred: List<String>, path: String): List<String> = if (path in starred) starred - path else starred + path

    /**
     * Whether the Favorites category is listed while [search] is typed. A search looks through every category, so
     * Favorites hides while one runs and each match shows once, in its own category.
     */
    fun listed(search: String) = search.isBlank()

    /** The starred ones of [all] settings, in the order of [all]. A path no setting has any longer is passed over. */
    fun <T> pick(all: List<T>, starred: List<String>, path: (T) -> String): List<T> = starred.toSet().let { set -> all.filter { path(it) in set } }
}
