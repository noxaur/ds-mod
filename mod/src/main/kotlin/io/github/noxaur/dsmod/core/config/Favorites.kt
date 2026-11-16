package io.github.noxaur.dsmod.core.config

/**
 * The settings the player starred, each by its path in `DsModConfig`, such as `slayer.slayerTracker`. The settings
 * show them in the order they are declared, so a starred setting sits where the player would look for it in its
 * own category, and not in the order they were starred. These are the rules of any settings screen, so the one that
 * replaces MoulConfig's follows them too. Nothing starts starred, because the player picks their favorites.
 */
object Favorites {
    /** [starred] with [path] starred, or no longer starred if it was. */
    fun toggle(starred: List<String>, path: String): List<String> = if (path in starred) starred - path else starred + path

    /**
     * Whether the Favorites category is listed while [search] is typed. A search looks through every category, so
     * Favorites hides while one runs and each match shows once, in its own category.
     */
    fun listed(search: String) = search.isBlank()

    /** The ones of [all] settings whose paths are in [paths], in the order of [all]. A path no setting has any longer is passed over. */
    fun <T> pick(all: List<T>, paths: Collection<String>, path: (T) -> String): List<T> = paths.toSet().let { set -> all.filter { path(it) in set } }
}

/**
 * The Favorites category while the player looks at it. A setting unstarred there stays listed, with its star empty,
 * until the player leaves the category, so the rows below do not move up under a second click, and a slip can be
 * starred again where it is.
 */
class FavoritesView {
    private val kept = LinkedHashSet<String>()

    /** The rows: the starred settings and the ones unstarred here, in the order of [all]. */
    fun <T> rows(all: List<T>, starred: List<String>, path: (T) -> String): List<T> = Favorites.pick(all, starred + kept, path)

    /** Stars or unstars [path], and keeps its row when it is unstarred [here], in this view. */
    fun toggle(starred: List<String>, path: String, here: Boolean): List<String> =
        Favorites.toggle(starred, path).also { if (here && path !in it) kept += path }

    /** The player left the category or closed the screen, so the rows unstarred here go. */
    fun left() = kept.clear()
}
