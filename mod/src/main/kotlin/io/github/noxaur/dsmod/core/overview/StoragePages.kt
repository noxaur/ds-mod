package io.github.noxaur.dsmod.core.overview

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.ContainerState
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.player.Seen
import io.github.noxaur.dsmod.core.text.count

/** @keep The Storage pages and the Ender Chest the storage overview shows. Their items are the player's data, so a page
 * keeps them over a restart and follows a switch of profile. How many pages the player has is counted from the root
 * menu's page icons and a page's `(2/7)`, for the profile it was counted on, since a page not bought has no icon. */
class StoragePages(private val feature: Feature) {
    private class Counted(val profile: String?, val pages: Int)

    private var root by feature.session { false }
    private var rootSize by feature.session { 0 }
    private var counted by feature.session { Counted(null, 0) }
    private var read: Map<String, Seen>? = null
    private var changes = 0

    /** @keep How many Storage pages the player has, and 0 before the root menu or a page's title was seen */
    val owned: Int get() = counted.takeIf { it.profile == feature.player.profile }?.pages ?: 0

    /** @keep Goes up whenever the pages change, so a reader rebuilds what it drew from them only when this moves */
    val version: Int
        get() {
            val now = feature.player.containers
            if (now !== read) {
                read = now
                changes++
            }
            return changes
        }

    fun page(id: String): Seen? = feature.player.containers[id]

    /** @keep Whether the player opened a Storage page or the Ender Chest on this profile */
    val any: Boolean get() = feature.player.containers.keys.any { it == Shelf.ENDER_CHEST_SHELF.id || number(it) != null }

    /** @keep The pages the overview shows, the Ender Chest first: every Storage page the player has, and any page seen
     * past that count. The menu `title` is the one opening, which can arrive before its events do. */
    fun shown(title: String?): List<Shelf> {
        val opening = Shelf.of(title)
        val kept = feature.player.containers.keys.mapNotNull(::number).maxOrNull()
        val last = listOfNotNull(owned, opening?.pages, opening?.id?.let(::number), kept).max()
        return listOf(Shelf.ENDER_CHEST_SHELF) + (1..last).map { Shelf.storage(it) }
    }

    init {
        feature.on<ContainerOpened> { opened, _ ->
            val title = opened.title?.plain
            root = title == ROOT
            rootSize = opened.size - ContainerState.PLAYER_SLOTS
            Shelf.of(title)?.pages?.let(::count)
        }
        feature.on<SlotChanged> { changed, _ ->
            if (!root || changed.slot >= rootSize) return@on
            changed.item?.name?.plain?.let(::pageOf)?.let(::number)?.let(::count)
        }
    }

    private fun count(pages: Int) {
        if (pages <= owned) return
        counted = Counted(feature.player.profile, pages)
        changes++
    }

    companion object {
        /** @keep The menu `/storage` opens, which holds an icon per page the player has */
        const val ROOT = "Storage"

        private const val STORAGE = "storage:"

        /** @keep A Storage page's number, from 1, by its id, and null for the Ender Chest */
        fun number(id: String): Int? = if (id.startsWith(STORAGE)) count(id.removePrefix(STORAGE))?.plus(1) else null
        private val ICON = Regex("Storage Page (\\d+)")

        /** @keep The page a root menu's icon opens, by the icon's name, such as `Storage Page 3` */
        fun pageOf(icon: String): String? {
            if (icon == Shelf.ENDER_CHEST_SHELF.title) return Shelf.ENDER_CHEST_SHELF.id
            val page = ICON.matchEntire(icon)?.groupValues?.get(1)?.let(::count)?.takeIf { it > 0 } ?: return null
            return "$STORAGE${page - 1}"
        }
    }
}
