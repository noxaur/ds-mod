package io.github.noxaur.dsmod.core.config

import io.github.noxaur.dsmod.core.ui.SettingHit

object Favorites {
    fun toggle(starred: List<String>, id: String): List<String> = if (id in starred) starred - id else starred + id

    fun listed(search: String) = search.isBlank()

    fun <T> pick(all: List<T>, ids: Collection<String>, id: (T) -> String): List<T> = ids.toSet().let { set -> all.filter { id(it) in set } }

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

class FavoritesView {
    private val kept = LinkedHashSet<String>()

    fun <T> rows(all: List<T>, starred: List<String>, id: (T) -> String): List<T> = Favorites.pick(all, starred + kept, id)

    fun toggle(starred: List<String>, id: String, here: Boolean): List<String> =
        Favorites.toggle(starred, id).also { if (here && id !in it) kept += id }

    fun left() = kept.clear()
}
