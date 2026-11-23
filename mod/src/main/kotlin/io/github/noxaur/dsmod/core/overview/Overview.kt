package io.github.noxaur.dsmod.core.overview

import io.github.noxaur.dsmod.core.feature.inventory.SkyBlockMenuClick
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.profile.Container
import io.github.noxaur.dsmod.core.profile.DrawnItem
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.count
import io.github.noxaur.dsmod.core.ui.ItemSearch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** @keep How an item is drawn: as the backend rebuilt it, or as the server sent it to a menu. */
sealed interface Look {
    data class Drawn(val item: DrawnItem) : Look

    data class Seen(val item: Item) : Look
}

/** @keep `id` is the server's item id, and two items are one entry when the id, the plain name and the rarity match. A
 * reforge or stars change the name, so they stay apart, and an upgrade that leaves the name alone merges. */
data class Owned(val look: Look, val id: String, val name: String, val lore: List<String>, val rarity: String?, val price: Double?, val count: Int) {
    val key get() = Key(id, name, rarity)

    data class Key(val id: String, val name: String, val rarity: String?)

    companion object {
        fun of(item: DrawnItem) = Owned(
            Look.Drawn(item),
            item.entry ?: item.material,
            item.name.plain,
            item.lore.map { it.plain },
            item.rarity,
            (item.facts?.get("npcSellPrice") as? JsonPrimitive)?.doubleOrNull,
            item.count,
        )

        fun of(item: Item) = Owned(
            Look.Seen(item),
            item.string("item_data", "item_id") ?: item.id,
            item.name?.plain ?: item.id,
            item.cleanLore,
            item.rarity,
            (item.objectAt("item_data")?.get("npc_sell_price") as? JsonPrimitive)?.doubleOrNull,
            item.count,
        )
    }
}

/** @keep One container's items, as one source last showed it. `kind` is the backend's id up to the colon, such as
 * `storage` for `storage:2`. */
data class Copy(val id: String, val title: String, val items: List<Owned?>) {
    val kind get() = id.removeSuffix(":other").substringBefore(':')

    companion object {
        fun of(container: Container) = Copy(container.id, container.title.plain, container.items.map { it?.let(Owned::of) })
    }
}

enum class Category(private val label: String, val kind: String?) {
    ALL("All", null),
    INVENTORY("Inventory", "inventory"),
    ENDER_CHEST("Ender Chest", "ender_chest"),
    STORAGE("Storage", "storage"),
    ACCESSORIES("Accessories", "accessories"),
    WARDROBE("Wardrobe", "wardrobe"),
    PETS("Pets", "pets"),
    ;

    override fun toString() = label
}

enum class SortBy(private val label: String) {
    AMOUNT("Amount"),
    VALUE("Value"),
    RARITY("Rarity"),
    NAME("Name"),
    ;

    override fun toString() = label
}

data class Place(val id: String, val title: String, val count: Int)

/** @keep A server menu that shows one container: which container, and the slots that hold its items. A Storage page's
 * top row holds its controls and the 45 slots under it its items, and `/storage <page>` opens a page itself. The Ender
 * Chest has one page of 27 under its controls, and `/ec` opens it. */
data class Shelf(val id: String, val title: String, val slots: IntRange) {
    companion object {
        private val STORAGE_PAGE = Regex("Storage Page \\((\\d+)/\\d+\\)")
        private const val ENDER_CHEST = "Ender Chest"
        private const val FIRST_ITEM = 9

        fun of(title: String?): Shelf? {
            if (title == ENDER_CHEST) return Shelf("ender_chest", ENDER_CHEST, FIRST_ITEM until FIRST_ITEM + 27)
            val page = title?.let(STORAGE_PAGE::matchEntire)?.groupValues?.get(1)?.let(::count) ?: return null
            return Shelf("storage:${page - 1}", "Storage $page", FIRST_ITEM until FIRST_ITEM + 45)
        }

        fun command(id: String): String? = when {
            id == "ender_chest" -> "ec"
            id.startsWith("storage:") -> count(id.removePrefix("storage:"))?.let { "storage ${it + 1}" }
            else -> null
        }
    }
}

data class Entry(val item: Owned, val count: Long, val places: List<Place>) {
    val value get() = item.price?.let { it * count }

    internal val rarityRank = item.rarity?.let { rarity -> Role.RARITIES.indexOfFirst { it.name == rarity } } ?: -1
}

/** @keep Built when the screen gets its items, and filtered and sorted only when the player changes the tab, the search
 * or the order, so a frame never walks the items. */
class Overview(copies: List<Copy>) {
    private val byCategory: Map<Category, List<Entry>> = Category.entries.associateWith { category ->
        merge(copies.filter { category.kind == null || it.kind == category.kind })
    }

    fun count(category: Category) = byCategory.getValue(category).size

    fun entries(category: Category, query: String, sort: SortBy, descending: Boolean): List<Entry> {
        val words = ItemSearch.words(query)
        val found = byCategory.getValue(category).filter { ItemSearch.matches(words, it.item.name, it.item.lore) }
        val order = when (sort) {
            SortBy.AMOUNT -> compareBy<Entry> { it.count }
            SortBy.VALUE -> compareBy { it.value ?: -1.0 }
            SortBy.RARITY -> compareBy { it.rarityRank }
            SortBy.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.item.name }
        }
        // @keep A tie reads A to Z either way, so flipping the order does not shuffle equal items.
        return found.sortedWith((if (descending) order.reversed() else order).thenBy(String.CASE_INSENSITIVE_ORDER) { it.item.name })
    }

    companion object {
        fun total(entries: List<Entry>) = entries.sumOf { it.value ?: 0.0 }

        private fun merge(copies: List<Copy>): List<Entry> {
            val found = LinkedHashMap<Owned.Key, Pair<Owned, LinkedHashMap<Pair<String, String>, Int>>>()
            for (copy in copies) for (item in copy.items) {
                // @keep The SkyBlock Menu item sits in every hotbar and is no item the player keeps.
                if (item == null || item.id == SkyBlockMenuClick.ITEM_ID) continue
                val (_, places) = found.getOrPut(item.key) { item to LinkedHashMap() }
                places.merge(copy.id to copy.title, item.count, Int::plus)
            }
            return found.values.map { (item, places) -> Entry(item, places.values.sumOf(Int::toLong), places.map { (where, count) -> Place(where.first, where.second, count) }) }
        }

        /** @keep A copy the player saw in a menu replaces the backend's when it was seen after dragsim.co last saved the
         * profile, because the backend's is then older. One the backend does not list, such as a page bought since,
         * is added. */
        fun copies(backend: List<Container>, savedAt: Long?, seen: List<Pair<Copy, Long>>): List<Copy> {
            val newer = seen.filter { (_, at) -> savedAt == null || at > savedAt }.associate { (copy, _) -> copy.id to copy }
            val listed = backend.filterNot { it.hidden }.map { newer[it.id] ?: Copy.of(it) }
            return listed + newer.values.filter { copy -> listed.none { it.id == copy.id } }
        }
    }
}
