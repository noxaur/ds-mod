package io.github.noxaur.dragen.core.feature.menus.helpers.storageoverview

import io.github.noxaur.dragen.core.game.ContainerOpened
import io.github.noxaur.dragen.core.game.Item
import io.github.noxaur.dragen.core.game.LeftServer
import io.github.noxaur.dragen.core.game.SlotChanged
import io.github.noxaur.dragen.core.overview.StoragePages
import io.github.noxaur.dragen.harness.Harness
import io.github.noxaur.dragen.harness.text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StorageOverviewTest : Harness() {
    private val storage = tree.feature<StorageOverview>()
    private val pages = storage.pages
    private var menu = 0

    private fun open(title: String, size: Int) = ContainerOpened(++menu, text(title), size)

    private fun item(name: String, count: Int = 1) = Item("minecraft:stone", count, text(name))

    private fun shown(title: String? = null) = pages.shown(title).map { it.id }

    @Test
    fun `an opened page is read from the player's data, with the controls left out`() {
        post(open("Storage Page (2/3)", 90), SlotChanged(0, item("Close")), SlotChanged(9, item("Dragon Scale", 3)), SlotChanged(53, item("Hot Potato Book")))
        val page = pages.page("storage:1")!!
        assertEquals(45, page.items.size)
        assertEquals("Dragon Scale" to 3, page.items[0]!!.name?.plain to page.items[0]!!.count)
        assertEquals("Hot Potato Book", page.items[44]!!.name?.plain)
        assertEquals(2, page.items.count { it != null }, "Close is a control")
        post(open("Ender Chest", 72), SlotChanged(9, item("Ender Pearl", 16)))
        assertEquals("Ender Pearl", pages.page("ender_chest")!!.items[0]!!.name?.plain)
        assertTrue(pages.any)
    }

    @Test
    fun `the overview shows the ender chest and every page the title counts`() {
        assertEquals(listOf("ender_chest"), shown(), "nothing seen yet")
        assertEquals(listOf("ender_chest", "storage:0", "storage:1", "storage:2"), shown("Storage Page (1/3)"), "the menu that is opening")
        post(open("Storage Page (2/3)", 90))
        assertEquals(listOf("ender_chest", "storage:0", "storage:1", "storage:2"), shown("Ender Chest"))
    }

    @Test
    fun `the root menu's page icons count the pages the player has`() {
        post(open(StoragePages.ROOT, 90), SlotChanged(10, item("Storage Page 1")), SlotChanged(11, item("Storage Page 4")), SlotChanged(12, item("Locked Page")))
        assertEquals(4, pages.owned)
        post(SlotChanged(60, item("Storage Page 9")))
        assertEquals(4, pages.owned, "an item in the player's inventory is no icon")
    }

    @Test
    fun `an icon names its page`() {
        assertEquals("storage:2", StoragePages.pageOf("Storage Page 3"))
        assertEquals("ender_chest", StoragePages.pageOf("Ender Chest"))
        assertNull(StoragePages.pageOf("Storage Page 0"))
        assertNull(StoragePages.pageOf("Locked Page"))
    }

    @Test
    fun `the version moves when a page changes, and only then`() {
        post(open("Storage Page (1/1)", 90))
        val before = pages.version
        assertEquals(before, pages.version)
        post(SlotChanged(9, item("Dragon Scale")))
        assertEquals(before + 1, pages.version)
    }

    @Test
    fun `the pages and their count outlast the session`() {
        post(open("Storage Page (1/3)", 90), SlotChanged(9, item("Dragon Scale")))
        post(LeftServer)
        post(open("Ender Chest", 72))
        assertEquals("Dragon Scale", pages.page("storage:0")!!.items[0]!!.name?.plain)
        assertEquals(listOf("ender_chest", "storage:0", "storage:1", "storage:2"), shown())
    }

    @Test
    fun `the player is told once that a page icon's text moved under Shift`() {
        post(open(StoragePages.ROOT, 90))
        assertEquals(emptyList(), game.messages, "no page is known, so no icon shows a preview")
        post(open("Storage Page (1/1)", 90), SlotChanged(9, item("Dragon Scale")), open(StoragePages.ROOT, 90))
        assertEquals(1, game.messages.size)
        assertTrue("hold Shift" in game.messages.single().detail.plain)
        game.messages.clear()
        post(open(StoragePages.ROOT, 90))
        assertEquals(emptyList(), game.messages)
    }

    @Test
    fun `overview mode does not tell about the preview`() {
        storage.mode.value = StorageMode.OVERVIEW
        post(open("Storage Page (1/1)", 90), SlotChanged(9, item("Dragon Scale")), open(StoragePages.ROOT, 90))
        assertEquals(emptyList(), game.messages)
    }
}
