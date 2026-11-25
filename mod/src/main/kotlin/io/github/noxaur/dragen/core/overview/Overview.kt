package io.github.noxaur.dragen.core.overview

import io.github.noxaur.dragen.core.feature.menus.betterui.skyblockmenuclick.SkyBlockMenuClick
import io.github.noxaur.dragen.core.game.ContainerState
import io.github.noxaur.dragen.core.game.Item
import io.github.noxaur.dragen.core.profile.Container
import io.github.noxaur.dragen.core.profile.DrawnItem
import io.github.noxaur.dragen.core.text.Role
import io.github.noxaur.dragen.core.text.count
import io.github.noxaur.dragen.core.ui.ItemSearch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

sealed interface Look {
    data class Drawn(val item: DrawnItem) : Look

    data class Seen(val item: Item) : Look
}

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

/** @keep A Storage page's top row holds its controls and the 45 slots under it its items, and `/storage <page>` opens a page itself. Its title,
 * `Storage Page (2/7)`, also gives how many pages the player has, as `pages`. The Ender Chest has one page of 27 under
 * its controls, and `/ec` opens it. */
data class Shelf(val id: String, val title: String, val slots: IntRange, val pages: Int? = null) {
    val rows get() = slots.count() / ContainerState.ROW_SLOTS

    companion object {
        private val STORAGE_PAGE = Regex("Storage Page \\((\\d+)/(\\d+)\\)")
        private const val ENDER_CHEST = "Ender Chest"
        private const val FIRST_ITEM = 9

        val ENDER_CHEST_SHELF = Shelf("ender_chest", ENDER_CHEST, FIRST_ITEM until FIRST_ITEM + 27)

        fun storage(page: Int, pages: Int? = null) = Shelf("storage:${page - 1}", "Storage $page", FIRST_ITEM until FIRST_ITEM + 45, pages)

        fun of(title: String?): Shelf? {
            if (title == ENDER_CHEST) return ENDER_CHEST_SHELF
            val match = title?.let(STORAGE_PAGE::matchEntire) ?: return null
            val page = count(match.groupValues[1]) ?: return null
            return storage(page, count(match.groupValues[2]))
        }

        fun command(id: String): String? = when {
            id == "ender_chest" -> "ec"
            id.startsWith("storage:") -> count(id.removePrefix("storage:"))?.let { "storage ${it + 1}" }
            else -> null
        }
    }
}

/** @keep dragsim.co gives each stack its own NPC price, so two stacks of one item can differ. */
data class Entry(val item: Owned, val count: Long, val places: List<Place>, val value: Double?, val each: Double?, val lore: List<String>) {

    internal val rarityRank = item.rarity?.let { rarity -> Role.RARITIES.indexOfFirst { it.name == rarity } } ?: -1
}

class Overview(copies: List<Copy>) {
    private val byCategory: Map<Category, List<Entry>> = Category.entries.associateWith { category ->
        merge(copies.filter { category.kind == null || it.kind == category.kind })
    }

    fun count(category: Category) = byCategory.getValue(category).size

    fun entries(category: Category, query: String, sort: SortBy, descending: Boolean): List<Entry> {
        val words = ItemSearch.words(query)
        val found = byCategory.getValue(category).filter { ItemSearch.matches(words, it.item.name, it.lore) }
        val order = when (sort) {
            SortBy.AMOUNT -> compareBy<Entry> { it.count }
            SortBy.VALUE -> compareBy { it.value ?: -1.0 }
            SortBy.RARITY -> compareBy { it.rarityRank }
            SortBy.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.item.name }
        }
        return found.sortedWith((if (descending) order.reversed() else order).thenBy(String.CASE_INSENSITIVE_ORDER) { it.item.name })
    }

    companion object {
        fun total(entries: List<Entry>) = entries.sumOf { it.value ?: 0.0 }

        private class Merged(val item: Owned) {
            val places = LinkedHashMap<Pair<String, String>, Int>()
            var value: Double? = null
            val prices = HashSet<Double?>()

            val lore = LinkedHashSet<String>()
        }

        private fun merge(copies: List<Copy>): List<Entry> {
            val found = LinkedHashMap<Owned.Key, Merged>()
            for (copy in copies) for (item in copy.items) {
                // @keep The SkyBlock Menu item sits in every hotbar and is no item the player keeps.
                if (item == null || item.id == SkyBlockMenuClick.ITEM_ID) continue
                val merged = found.getOrPut(item.key) { Merged(item) }
                merged.places.merge(copy.id to copy.title, item.count, Int::plus)
                merged.prices += item.price
                merged.lore += item.lore
                item.price?.let { merged.value = (merged.value ?: 0.0) + it * item.count }
            }
            return found.values.map { m ->
                Entry(m.item, m.places.values.sumOf(Int::toLong), m.places.map { (where, count) -> Place(where.first, where.second, count) }, m.value, m.prices.singleOrNull(), m.lore.toList())
            }
        }

        /** @keep A Storage page or the Ender Chest changes only while its menu is open, where the mod reads it, so a copy
         * the mod kept is never older than dragsim.co's, unless the player changed it from a game without Dragen since.
         * Comparing with dragsim.co's save time was rejected, because its clock need not agree with ours. */
        fun copies(backend: List<Container>, seen: List<Copy>): List<Copy> {
            val used = seen.associateBy { it.id }
            val listed = backend.filterNot { it.hidden }.map { used[it.id] ?: Copy.of(it) }
            return listed + used.values.filter { copy -> listed.none { it.id == copy.id } }
        }
    }
}
