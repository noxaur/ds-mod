package io.github.noxaur.dsmod.core.config

import io.github.noxaur.dsmod.core.ui.SettingHit

/**
 * The settings the player starred, each by its row's id. The settings show them in the order of the tree, so a starred
 * setting sits where the player would look for it in its own section, and not in the order they were starred.
 * Nothing starts starred, because the player picks their favorites.
 */
object Favorites {
    /** [starred] with [id] starred, or no longer starred if it was. */
    fun toggle(starred: List<String>, id: String): List<String> = if (id in starred) starred - id else starred + id

    /**
     * Whether Favorites is listed while [search] is typed. A search looks through every section, so Favorites hides
     * while one runs and each match shows once, in its own section.
     */
    fun listed(search: String) = search.isBlank()

    /** The ones of [all] settings whose ids are in [ids], in the order of [all]. An id no setting has any longer is passed over. */
    fun <T> pick(all: List<T>, ids: Collection<String>, id: (T) -> String): List<T> = ids.toSet().let { set -> all.filter { id(it) in set } }

    /**
     * The rows of [sections] whose ids are in [shown], under the group each sits in, in the tree's order. A group's
     * heading switch is a row of its own here. A row under one that shows is left out, since it opens under it.
     */
    fun hits(sections: List<SettingSection>, shown: Collection<String>): List<SettingHit> {
        val set = shown.toSet()
        fun MutableList<Setting>.collect(row: Setting) {
            if (row.id in set) add(row) else row.children.forEach { collect(it) }
        }
        return sections.withIndex().flatMap { (i, section) ->
            section.groups.mapNotNull { group ->
                val rows = ArrayList<Setting>()
                group.switch?.let { rows.collect(it) }
                group.rows.forEach { rows.collect(it) }
                if (rows.isEmpty()) null else SettingHit(i, group, rows, emptySet())
            }
        }
    }
}

/**
 * Favorites while the player looks at it. A setting unstarred there stays listed, with its star empty, until the
 * player leaves, so the rows below do not move up under a second click, and a slip can be starred again where it is.
 */
class FavoritesView {
    private val kept = LinkedHashSet<String>()

    /** The rows: the starred settings and the ones unstarred here, in the order of [all]. */
    fun <T> rows(all: List<T>, starred: List<String>, id: (T) -> String): List<T> = Favorites.pick(all, starred + kept, id)

    /** Stars or unstars [id], and keeps its row when it is unstarred [here], in this view. */
    fun toggle(starred: List<String>, id: String, here: Boolean): List<String> =
        Favorites.toggle(starred, id).also { if (here && id !in it) kept += id }

    /** The player left Favorites or closed the screen, so the rows unstarred here go. */
    fun left() = kept.clear()
}
