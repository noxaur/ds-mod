package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.harness.Harness
import io.github.noxaur.dsmod.harness.ownInventory
import io.github.noxaur.dsmod.harness.text
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.hud.HudDock
import io.github.noxaur.dsmod.core.hud.HudLook
import io.github.noxaur.dsmod.core.hud.ItemsRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class InventoryHudTest : Harness() {
    private val feature = tree.feature<InventoryHud>().also { it.feature.enabled = true }

    private val sword = Item("minecraft:golden_sword", 1)
    private val eyes = Item("minecraft:ender_eye", 16)
    private val arrow = Item("minecraft:arrow", 1)

    private fun itemRows() = hud(feature)?.rows?.map { it as ItemsRow }

    private fun row(vararg items: Pair<Int, Item>) = List(9) { column -> items.firstOrNull { it.first == column }?.second }

    @Test
    fun `the three rows show nine to a row with no header, a gap for each empty slot, and the hotbar left out`() {
        post(*ownInventory(9 to eyes, 26 to arrow, 36 to sword))
        assertEquals("", hud(feature)?.header?.plain)
        assertEquals(listOf(row(0 to eyes), row(8 to arrow), row()), itemRows()?.map { it.items })
        assertEquals(listOf(false, false, false), itemRows()?.map { it.apart })
    }

    @Test
    fun `the hotbar sits apart from the rows beside it, in the order the player dragged them`() {
        feature.shownRows.value = listOf(InventoryRow.HOTBAR, InventoryRow.TOP, InventoryRow.BOTTOM)
        post(*ownInventory(9 to eyes, 36 to sword))
        assertEquals(listOf(row(0 to sword), row(0 to eyes), row()), itemRows()?.map { it.items })
        assertEquals(listOf(false, true, false), itemRows()?.map { it.apart })
    }

    @Test
    fun `the last inventory seen stays while another menu is open, and its slots are not read as the inventory's`() {
        post(*ownInventory(9 to eyes))
        val before = hud(feature)
        post(ContainerOpened(5, text("Catacombs - Floor I"), 72), SlotChanged(9, arrow))
        assertSame(before, hud(feature))
        post(*ownInventory(9 to eyes, 10 to arrow))
        assertEquals(row(0 to eyes, 1 to arrow), itemRows()?.first()?.items)
    }

    @Test
    fun `the armor and the offhand show their own slots, each apart from the rows beside it`() {
        val helmet = Item("minecraft:leather_helmet", 1)
        val shield = Item("minecraft:shield", 1)
        feature.shownRows.value = listOf(InventoryRow.TOP, InventoryRow.MIDDLE, InventoryRow.ARMOR, InventoryRow.OFFHAND)
        post(*ownInventory(5 to helmet, 9 to eyes, 45 to shield))
        assertEquals(listOf(row(0 to eyes), row(), listOf(helmet, null, null, null), listOf(shield)), itemRows()?.map { it.items })
        assertEquals(listOf(false, false, true, true), itemRows()?.map { it.apart })
        assertEquals(listOf(9, 18, 5, 45), itemRows()?.map { it.slot })
    }

    @Test
    fun `the layout, the panel's opacity and the pickup animation reach the block`() {
        post(*ownInventory(9 to eyes))
        assertEquals(false, hud(feature)?.columns)
        assertEquals(1f, hud(feature)?.opacity)
        assertEquals(false, hud(feature)?.pops)
        feature.layout.value = InventoryLayout.COLUMNS
        feature.opacity.value = 40f
        feature.pickup.value = true
        pass()
        assertEquals(true, hud(feature)?.columns)
        assertEquals(0.4f, hud(feature)?.opacity)
        assertEquals(true, hud(feature)?.pops)
    }

    @Test
    fun `nothing shows while every slot picked is empty`() {
        post(*ownInventory(36 to sword))
        assertNull(hud(feature))
        feature.shownRows.value = listOf(InventoryRow.HOTBAR)
        pass()
        assertEquals(listOf(row(0 to sword)), itemRows()?.map { it.items })
    }

    @Test
    fun `the block is not rebuilt when nothing it shows changed`() {
        post(*ownInventory(9 to eyes))
        val before = hud(feature)
        post(SlotChanged(5, Item("minecraft:leather_helmet", 1)))
        assertSame(before, hud(feature))
    }

    @Test
    fun `the grid looks like the inventory unless the player picks another look or a place by the hotbar`() {
        post(*ownInventory(9 to eyes))
        assertEquals(HudLook.INVENTORY, hud(feature)?.look)
        assertEquals(HudDock.FREE, hud(feature)?.dock)
        feature.look.value = InventoryLook.PLATE
        feature.placement.value = HudDock.RIGHT_OF_HOTBAR
        pass()
        assertEquals(HudLook.PLATE, hud(feature)?.look)
        assertEquals(HudDock.RIGHT_OF_HOTBAR, hud(feature)?.dock)
    }

    @Test
    fun `each row names its first inventory slot, so the HUD can mark it, and the grid steps aside for menus`() {
        feature.shownRows.value = listOf(InventoryRow.BOTTOM, InventoryRow.HOTBAR)
        post(*ownInventory(27 to arrow, 36 to sword))
        assertEquals(listOf(27, 36), itemRows()?.map { it.slot })
        assertEquals(false, hud(feature)?.overMenus)
    }
}
