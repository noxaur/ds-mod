package io.github.noxaur.dsmod.core.overview

import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.profile.Profile
import io.github.noxaur.dsmod.harness.lines
import io.github.noxaur.dsmod.harness.resource
import io.github.noxaur.dsmod.harness.text
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class OverviewTest {
    private fun container(name: String) = Profile.parseContainer(resource("profiles/containers/$name.json"))

    private val inventory = container("inventory")
    private val enderChest = container("ender_chest")
    private val storage = container("storage-0")

    private fun seen(itemId: String, name: String, count: Int, rarity: String = "COMMON", price: Double? = null, lore: List<String> = emptyList()) = Owned.of(
        Item(
            "minecraft:stone",
            count,
            text(name),
            lines(*lore.toTypedArray()),
            buildJsonObject {
                put("item_data", buildJsonObject {
                    put("item_id", itemId)
                    put("rarity", rarity)
                    price?.let { put("npc_sell_price", it) }
                })
            },
        ),
    )

    private fun names(entries: List<Entry>) = entries.map { "${it.item.name} x${it.count}" }

    @Test
    fun `the same item in two slots is one entry with the summed count, naming each place`() {
        val overview = Overview(listOf(Copy.of(enderChest)))
        val residue = overview.entries(Category.ALL, "residue", SortBy.AMOUNT, descending = true).single()
        assertEquals(128, residue.count)
        assertEquals(listOf(Place("ender_chest", "Ender Chest", 128)), residue.places)
    }

    @Test
    fun `an item kept in two containers lists both, in the order the profile lists them`() {
        val page = Copy("storage:1", "Storage 2", listOf(seen("ritual_residue", "Ritual Residue", 10, "EPIC")))
        val residue = Overview(listOf(Copy.of(enderChest), page)).entries(Category.ALL, "residue", SortBy.AMOUNT, true).single()
        assertEquals(138, residue.count)
        assertEquals(listOf(Place("ender_chest", "Ender Chest", 128), Place("storage:1", "Storage 2", 10)), residue.places)
    }

    @Test
    fun `a different name or rarity keeps two items of one id apart`() {
        val copy = Copy("inventory", "Inventory", listOf(
            seen("dragon_sword", "Dragon Sword", 1, "EPIC"),
            seen("dragon_sword", "Sharp Dragon Sword", 1, "EPIC"),
            seen("dragon_sword", "Dragon Sword", 1, "LEGENDARY"),
            seen("dragon_sword", "Dragon Sword", 1, "EPIC"),
        ))
        assertEquals(listOf("Dragon Sword x2", "Dragon Sword x1", "Sharp Dragon Sword x1"), names(Overview(listOf(copy)).entries(Category.ALL, "", SortBy.AMOUNT, true)))
    }

    @Test
    fun `a tab holds only its own kind of container`() {
        val overview = Overview(listOf(Copy.of(inventory), Copy.of(enderChest), Copy.of(storage)))
        assertEquals(listOf("Aspect of the End", "Voidfang Dagger"), overview.entries(Category.STORAGE, "", SortBy.NAME, false).map { it.item.name })
        assertEquals(overview.count(Category.INVENTORY) + overview.count(Category.ENDER_CHEST) + overview.count(Category.STORAGE), overview.count(Category.ALL))
    }

    @Test
    fun `the search matches the name or a lore line, ignoring case`() {
        val copy = Copy("inventory", "Inventory", listOf(seen("a", "Dragon Scale", 3), seen("b", "Blade", 1, lore = listOf("Forged from a DRAGON")), seen("c", "Dirt", 9)))
        assertEquals(listOf("Blade", "Dragon Scale"), Overview(listOf(copy)).entries(Category.ALL, "dragon", SortBy.NAME, false).map { it.item.name })
    }

    @Test
    fun `each order sorts both ways, and a tie goes by name`() {
        val copy = Copy("inventory", "Inventory", listOf(
            seen("a", "Bone", 5, "RARE", price = 2.0),
            seen("b", "Apple", 5, "COMMON", price = 10.0),
            seen("c", "Crown", 1, "MYTHIC"),
        ))
        val overview = Overview(listOf(copy))
        fun order(sort: SortBy, descending: Boolean) = overview.entries(Category.ALL, "", sort, descending).map { it.item.name }
        assertEquals(listOf("Crown", "Apple", "Bone"), order(SortBy.AMOUNT, false))
        assertEquals(listOf("Apple", "Bone", "Crown"), order(SortBy.AMOUNT, true))
        assertEquals(listOf("Apple", "Bone", "Crown"), order(SortBy.VALUE, true))
        assertEquals(listOf("Crown", "Bone", "Apple"), order(SortBy.RARITY, true))
        assertEquals(listOf("Apple", "Bone", "Crown"), order(SortBy.NAME, false))
    }

    @Test
    fun `the total is the npc value of what the search left, and an item with no price adds nothing`() {
        val copy = Copy("inventory", "Inventory", listOf(seen("a", "Bone", 5, price = 2.0), seen("b", "Apple", 3, price = 10.0), seen("c", "Crown", 1)))
        val overview = Overview(listOf(copy))
        assertEquals(40.0, Overview.total(overview.entries(Category.ALL, "", SortBy.AMOUNT, true)))
        assertEquals(10.0, Overview.total(overview.entries(Category.ALL, "bone", SortBy.AMOUNT, true)))
    }

    @Test
    fun `a copy seen after dragsim co saved the profile replaces the backend's, and an older one does not`() {
        val live = Copy("inventory", "Inventory", listOf(seen("dirt", "Dirt", 1)))
        val page = Copy("storage:7", "Storage 8", listOf(seen("dirt", "Dirt", 2)))
        val newer = Overview.copies(listOf(inventory, enderChest), savedAt = 100, seen = listOf(live to 101L, page to 101L))
        assertEquals(listOf(live, Copy.of(enderChest), page), newer)
        val older = Overview.copies(listOf(inventory, enderChest), savedAt = 100, seen = listOf(live to 99L))
        assertEquals(listOf(Copy.of(inventory), Copy.of(enderChest)), older)
    }

    @Test
    fun `a container the player hid lists nothing`() {
        assertEquals(emptyList(), Overview.copies(listOf(enderChest.copy(hidden = true, items = emptyList())), savedAt = null, seen = emptyList()))
    }

    @Test
    fun `a backend item's id and price come from what the backend drew`() {
        val drawn = storage.items.filterNotNull().first().let { it.copy(facts = JsonObject(mapOf("npcSellPrice" to JsonPrimitive(25)))) }
        val owned = Owned.of(drawn)
        assertEquals("voidfang_dagger", owned.id)
        assertEquals(25.0, owned.price)
    }
}
