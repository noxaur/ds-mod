package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.features
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.hud.HudLook
import io.github.noxaur.dsmod.core.hud.ItemsRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class InventoryPreviewTest {
    private val config = DsModConfig().apply { general.inventoryPreview = true }
    private val game = Game(features(config))
    private var time = 0L

    private val sword = Item("minecraft:golden_sword", 1)
    private val eyes = Item("minecraft:ender_eye", 16)
    private val arrow = Item("minecraft:arrow", 1)

    private fun post(vararg events: GameEvent) {
        for (event in events) game.post(++time, event)
        // A readout refreshes at least every 250 ms, which is how a changed setting shows with no event.
        time += 250
        game.tick(time)
    }

    /** The player's own inventory, with [items] by slot. */
    private fun inventory(vararg items: Pair<Int, Item>) =
        arrayOf<GameEvent>(ContainerOpened(0, null, 46), *items.map { (slot, item) -> SlotChanged(slot, item) }.toTypedArray())

    private fun hud() = game.readouts.single { it.feature.id == "inventory-preview" }.hud

    private fun rows() = hud()?.rows?.map { it as ItemsRow }

    private fun row(vararg items: Pair<Int, Item>) = List(9) { column -> items.firstOrNull { it.first == column }?.second }

    @Test
    fun `the three rows show nine to a row with no header, a gap for each empty slot, and the hotbar left out`() {
        post(*inventory(9 to eyes, 26 to arrow, 36 to sword))
        assertEquals("", hud()?.header?.plain)
        assertEquals(listOf(row(0 to eyes), row(8 to arrow), row()), rows()?.map { it.items })
        assertEquals(listOf(false, false, false), rows()?.map { it.apart })
    }

    @Test
    fun `the hotbar sits apart from the rows beside it, in the order the player dragged them`() {
        config.general.inventoryRows = mutableListOf(InventoryRow.HOTBAR, InventoryRow.TOP, InventoryRow.BOTTOM)
        post(*inventory(9 to eyes, 36 to sword))
        assertEquals(listOf(row(0 to sword), row(0 to eyes), row()), rows()?.map { it.items })
        assertEquals(listOf(false, true, false), rows()?.map { it.apart })
    }

    @Test
    fun `the last inventory seen stays while another menu is open, and its slots are not read as the inventory's`() {
        post(*inventory(9 to eyes))
        val before = hud()
        post(ContainerOpened(5, ServerText(ServerSpan("Catacombs - Floor I")), 72), SlotChanged(9, arrow))
        assertSame(before, hud())
        post(*inventory(9 to eyes, 10 to arrow))
        assertEquals(row(0 to eyes, 1 to arrow), rows()?.first()?.items)
    }

    @Test
    fun `nothing shows while every slot picked is empty`() {
        post(*inventory(36 to sword))
        assertNull(hud())
        config.general.inventoryRows = mutableListOf(InventoryRow.HOTBAR)
        post()
        assertEquals(listOf(row(0 to sword)), rows()?.map { it.items })
    }

    @Test
    fun `the block is not rebuilt when nothing it shows changed`() {
        post(*inventory(9 to eyes))
        val before = hud()
        post(SlotChanged(5, Item("minecraft:leather_helmet", 1)))
        assertSame(before, hud())
    }

    @Test
    fun `the look is the player's`() {
        post(*inventory(9 to eyes))
        config.general.inventoryLook = HudLook.HOTBAR
        post()
        assertEquals(HudLook.HOTBAR, hud()?.look)
    }
}
