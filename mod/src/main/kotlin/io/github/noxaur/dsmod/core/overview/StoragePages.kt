package io.github.noxaur.dsmod.core.overview

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.ContainerState
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.text.count

/** @keep Each Storage page and the Ender Chest as the player last saw them this session, shared by every feature that
 * shows them. A copy lasts the session, because a copy kept on disk could outlive a switch to another profile. Each
 * slot is read as it arrives, so a menu costs one item per change. Each feature that follows the pages hears every
 * event, so an event one of them already took is skipped. */
class StoragePages {
    class Page(val shelf: Shelf, val items: Array<Owned?>)

    private val kept = LinkedHashMap<String, Page>()
    private var session = -1
    private var last: GameEvent? = null
    private var root = false
    private var rootSize = 0

    var open: Page? = null
        private set

    /** @keep How many Storage pages the player has, from the root menu's page icons or a page's `(2/7)`, and 0 before
     * either was seen. A page that is not bought has no icon, so it is not counted. */
    var owned = 0
        private set

    /** @keep Goes up on every change, so a reader rebuilds what it drew from the pages only when this moves */
    var version = 0
        private set

    fun page(id: String): Page? = kept[id]

    val pages: Collection<Page> get() = kept.values

    val copies: List<Copy> get() = kept.values.map { Copy(it.shelf.id, it.shelf.title, it.items.toList()) }

    /** @keep The pages the overview shows, the Ender Chest first: every Storage page the player has, and any page seen
     * past that count. The menu `title` is the one opening, which can arrive before its events do. */
    fun shown(title: String?): List<Shelf> {
        val opening = Shelf.of(title)
        val last = listOfNotNull(owned, opening?.pages, opening?.id?.let(::number), kept.keys.mapNotNull(::number).maxOrNull()).max()
        return listOf(Shelf.ENDER_CHEST_SHELF) + (1..last).map { Shelf.storage(it) }
    }

    fun follow(feature: Feature) {
        feature.on<ContainerOpened> { opened, _ ->
            if (!take(feature, opened)) return@on
            val title = opened.title?.plain
            root = title == ROOT
            rootSize = opened.size - ContainerState.PLAYER_SLOTS
            val shelf = Shelf.of(title)?.takeIf { it.slots.last < rootSize }
            open = shelf?.let { Page(it, arrayOfNulls(it.slots.count())) }?.also { kept[it.shelf.id] = it }
            shelf?.pages?.let { owned = maxOf(owned, it) }
            version++
        }
        feature.on<SlotChanged> { changed, _ ->
            if (!take(feature, changed)) return@on
            if (root) {
                val page = changed.item?.name?.plain?.let(::pageOf)?.let(::number)
                if (page != null && changed.slot < rootSize && page > owned) {
                    owned = page
                    version++
                }
                return@on
            }
            val menu = open ?: return@on
            val i = changed.slot - menu.shelf.slots.first
            if (i !in menu.items.indices) return@on
            menu.items[i] = changed.item?.let(Owned::of)
            version++
        }
    }

    private fun take(feature: Feature, event: GameEvent): Boolean {
        if (session != feature.sessionNumber) {
            session = feature.sessionNumber
            kept.clear()
            open = null
            owned = 0
            root = false
            version++
        }
        if (event === last) return false
        last = event
        return true
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
