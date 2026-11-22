package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.InventoryHud
import io.github.noxaur.dsmod.core.feature.general.InventoryRow
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.hud.HudGroup
import io.github.noxaur.dsmod.core.hud.ItemsRow
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import io.github.noxaur.dsmod.gametest.InventoryGrid.FILLED
import io.github.noxaur.dsmod.gametest.InventoryGrid.ID
import io.github.noxaur.dsmod.gametest.InventoryGrid.cell
import io.github.noxaur.dsmod.gametest.InventoryGrid.stackAt
import io.github.noxaur.dsmod.gametest.InventoryGrid.waitForItem
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Items

/**
 * The inventory shows on the HUD as a grid, nine to a row, so a head keeps its skin, dyed leather its color and a stack
 * its count, and the chat's free mouse reads an item's tooltip. The hotbar, the armor and the offhand join it each a
 * little apart, and the grid never empties while the inventory is read again after a menu, which takes a few ticks for
 * items with long lore as dragsim.co's have. Read the screenshots: the grid, the tooltip, and every row.
 */
class InventoryHudTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = InventoryGrid.with(context) { world ->
        val inventoryHud = DsModClient.feature<InventoryHud>()
        check(HeadSkin.worn(stackAt(context, cell(context, 0, 0)))) { "The first item's icon lost its skin" }
        check(stackAt(context, cell(context, 0, 1))?.get(DataComponents.DYED_COLOR)?.rgb() == InventoryGrid.DYE) { "The leather lost its dye" }
        check(stackAt(context, cell(context, 1, 4))?.count == 13) { "The middle row's fifth stack is not 13 strong" }
        check(stackAt(context, cell(context, 2, 8))?.hoverName?.string == "Item 35") { "The last cell is not the bottom row's last slot" }
        check(stackAt(context, cell(context, 0, 3)) == null) { "An empty slot has an icon" }
        context.takeScreenshot("inventory-hud")

        context.openChat()
        val dyed = cell(context, 0, 1)
        context.point(dyed.first, dyed.second, settle = 2)
        context.takeScreenshot("inventory-hud-tooltip")
        context.runOnClient<Nothing> { it.gui.setScreen(null) }

        // Every row, each that is not the inventory's own standing apart: the hotbar, then the armor, then the offhand.
        world.server.runOnServer<Nothing> { server ->
            val player = server.player
            player.setItemSlot(EquipmentSlot.HEAD, ServerMenus.named(Items.LEATHER_HELMET, "Helmet"))
            player.setItemSlot(EquipmentSlot.OFFHAND, ServerMenus.named(Items.SHIELD, "Shield"))
        }
        inventoryHud.shownRows.value = InventoryRow.entries
        waitForItem(context, "Shield") { cell(context, 5, 0, gaps = 3) }
        check(stackAt(context, cell(context, 3, 0, gaps = 1))?.hoverName?.string == "Item 0") { "The hotbar's first item is not under the gap" }
        check(stackAt(context, cell(context, 4, 0, gaps = 2))?.hoverName?.string == "Helmet") { "The armor row does not start with the helmet" }
        check(stackAt(context, cell(context, 4, 1, gaps = 2)) == null) { "The armor row shows a chestplate the player does not wear" }
        context.takeScreenshot("inventory-hud-every-row")

        // A slot that changes hands the whole grid to the HUD again, which rebuilds each stack once.
        val (fresh, again) = context.computeOnClient<Pair<Long, Long>, Nothing> {
            val block = checkNotNull(DsModClient.game.readouts.first { readout -> readout.feature.id == ID }.hud)
            val group = HudGroup("probe", 0, 0)
            fun micros(rounds: Int, show: () -> Unit): Long {
                val start = System.nanoTime()
                repeat(rounds) { show() }
                return (System.nanoTime() - start) / rounds / 1000
            }
            repeat(5) { Hud.show("probe", group, block) }
            val fresh = micros(20) { Hud.hide("probe"); Hud.show("probe", group, block) }
            val again = micros(20) { Hud.show("probe", group, block) }
            Hud.hide("probe")
            fresh to again
        }
        println("InventoryHudTest: handing the HUD a grid of ${FILLED + 2} items took ${fresh}µs new and ${again}µs again")

        // Back in the inventory after a menu, the long-lored items are read again over a few ticks, and the grid keeps them all.
        var sampling = false
        val shown = ArrayList<Int>()
        ClientTickEvents.END_CLIENT_TICK.register {
            if (!sampling) return@register
            val block = DsModClient.game.readouts.first { readout -> readout.feature.id == ID }.hud
            shown += block?.rows?.sumOf { row -> (row as ItemsRow).items.count { item -> item != null } } ?: 0
        }
        ServerMenus.open(context, world, 6, "Big Chest")
        context.runOnClient<Nothing> {
            sampling = true
            it.player!!.closeContainer()
        }
        context.waitTicks(20)
        context.runOnClient<Nothing> { sampling = false }
        check(shown.isNotEmpty() && shown.all { it == FILLED + 2 }) { "The grid did not hold all ${FILLED + 2} items as the inventory was read again: $shown" }

        // A capture that starts inside a menu has seen none of the inventory, so going back must send it whole.
        ServerMenus.open(context, world, 6, "Big Chest")
        val tapped = ArrayList<GameEvent>()
        context.runOnClient<Nothing> { DsModClient.game.tap = { _, event -> tapped += event } }
        // The items sent since the last menu opened, which once back in the inventory are the inventory's.
        fun since() = tapped.drop(tapped.indexOfLast { it is ContainerOpened }).count { it is SlotChanged && it.item != null }
        try {
            context.waitTick()
            context.runOnClient<Nothing> { it.player!!.closeContainer() }
            context.waitFor { (tapped.lastOrNull { it is ContainerOpened } as ContainerOpened?)?.id == 0 && since() >= FILLED + 2 }
        } finally {
            context.runOnClient<Nothing> { DsModClient.game.tap = null }
        }
        val back = tapped.filterIsInstance<ContainerOpened>().last()
        val slots = since()
        check(back.id == 0 && !back.kept && slots == FILLED + 2) { "A capture started in a menu got the inventory as $back with $slots items" }
    }
}
