package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.overview.Category
import io.github.noxaur.dsmod.core.overview.Overview
import io.github.noxaur.dsmod.core.overview.Owned
import io.github.noxaur.dsmod.core.overview.Shelf
import io.github.noxaur.dsmod.core.overview.SortBy
import io.github.noxaur.dsmod.harness.Harness
import io.github.noxaur.dsmod.harness.readCapture
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItemOverviewTest : Harness(alone = ItemOverview::class) {
    private val overview = tree.feature<ItemOverview>()
    private val events = readCapture(Path.of("src/test/resources/features/item-overview/storage-and-ender-chest.jsonl")).map { it.event }

    private val storagePage = events.take(events.indices.filter { events[it] is ContainerOpened }[2])

    private fun marks() = overview.feature.slots!!(time)

    private fun hotPotatoBook() = overview.copies.first { it.id == "storage:1" }.items.firstNotNullOf { it?.takeIf { item -> item.name == "Hot Potato Book" } }.key

    @Test
    fun `a storage page and the ender chest are kept as the player saw them`() {
        post(*events.toTypedArray())
        marks()
        val copies = overview.copies
        assertEquals(listOf("inventory" to "Inventory", "storage:1" to "Storage 2", "ender_chest" to "Ender Chest"), copies.map { it.id to it.title })
        assertEquals(45, copies[1].items.size)
        assertEquals(27, copies[2].items.size)
        val books = Overview(copies).entries(Category.ALL, "hot potato", SortBy.AMOUNT, true)
        assertEquals(listOf("Hot Potato Book" to 27L, "Golden Hot Potato Book" to 1L), books.map { it.item.name to it.count })
        assertNull(copies[2].items.firstOrNull { it?.name == "Close" }, "the controls are not items")
    }

    @Test
    fun `a clicked item is marked in the menu that holds it until the highlight time is up`() {
        post(*storagePage.toTypedArray())
        marks()
        val key: Owned.Key = hotPotatoBook()
        overview.find(key, time)
        assertEquals(setOf(52, 53), marks().keys, "both stacks of the book")
        pass((overview.highlight.value * 1000).toLong() - 1)
        assertEquals(setOf(52, 53), marks().keys)
        pass(1)
        assertTrue(marks().isEmpty())
    }

    @Test
    fun `a mark waits for the menu it was asked for`() {
        post(*storagePage.toTypedArray())
        marks()
        val key = hotPotatoBook()
        post(*events.drop(storagePage.size).toTypedArray())
        overview.find(key, time)
        assertTrue(marks().isEmpty(), "the ender chest has no book")
        post(*storagePage.toTypedArray())
        assertEquals(setOf(52, 53), marks().keys)
    }

    @Test
    fun `only a storage page and the ender chest open from the overview`() {
        assertEquals("storage 3", Shelf.command("storage:2"))
        assertEquals("ec", Shelf.command("ender_chest"))
        assertNull(Shelf.command("inventory"))
        assertNull(Shelf.command("wardrobe:0"))
        assertNull(Shelf.of("Storage"), "the root menu lists pages and holds no items")
    }
}
