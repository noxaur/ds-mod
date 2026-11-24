package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.hud.inventoryhud.InventoryHud
import io.github.noxaur.dsmod.core.feature.hud.inventoryhud.InventoryLook
import io.github.noxaur.dsmod.core.feature.menus.look.menutheme.MenuTheme
import io.github.noxaur.dsmod.core.hud.HudDock
import io.github.noxaur.dsmod.core.ui.MenuLook
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import java.nio.file.Path
import io.github.noxaur.dsmod.edge.Hud
import io.github.noxaur.dsmod.gametest.InventoryGrid.ID
import io.github.noxaur.dsmod.gametest.InventoryGrid.cell
import io.github.noxaur.dsmod.gametest.InventoryGrid.pixel
import io.github.noxaur.dsmod.gametest.InventoryGrid.showHotbar
import io.github.noxaur.dsmod.gametest.InventoryGrid.waitForItem
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class InventoryMarksTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = InventoryGrid.with(context) { world ->
        val inventoryHud = DsModClient.feature<InventoryHud>()
        val menuTheme = DsModClient.feature<MenuTheme>()
        showHotbar(context)

        context.runOnClient<Nothing> { it.player!!.inventory.selectedSlot = 2 }
        context.waitTick()
        val heldShot = context.takeScreenshot("inventory-hud-held")
        val ring = Theme.CHARCOAL.accent and 0xFFFFFF
        fun ringed(column: Int) = pixel(context, heldShot, cell(context, 3, column, gaps = 1), 0, -9) == ring
        check(ringed(2)) { "The held slot has no ring" }
        check(!ringed(3)) { "A slot the player does not hold has a ring" }

        check(pixel(context, heldShot, cell(context, 0, 6), -7, -7) != pixel(context, heldShot, cell(context, 1, 2), -7, -7)) {
            "The Legendary sword's slot looks like the plain sword's"
        }

        val obsidian = cell(context, 3, 1, gaps = 1)
        fun land() = context.runOnClient<Nothing> { it.player!!.inventory.getItem(1).popTime = POP }
        val still = pixel(context, heldShot, obsidian, -6, 0)
        land()
        val unpopped = pixel(context, context.takeScreenshot("inventory-hud-pickup-off"), obsidian, -6, 0)
        check(unpopped == still) { "The grid bobbed a stack with Pickup Animation off" }
        inventoryHud.pickup.value = true
        context.waitFor { DsModClient.game.readouts.first { it.feature.id == ID }.hud?.pops == true }
        land()
        val popped = pixel(context, context.takeScreenshot("inventory-hud-pickup"), obsidian, -6, 0)
        check(popped != still) { "A stack that landed did not bob: %06X".format(popped) }
        inventoryHud.pickup.value = false

        fun menuShot(name: String): Path {
            ServerMenus.open(context, world, 1, "Small Chest")
            return context.takeScreenshot(name)
        }
        fun closeMenu() {
            context.runOnClient<Nothing> { it.player!!.closeContainer() }
            context.waitFor { it.gui.screen() == null }
        }
        val menuLookBefore = menuTheme.menuLook.value
        val glassBefore = menuTheme.glassOpacity.value
        menuTheme.menuLook.value = MenuLook.GLASS
        menuTheme.glassOpacity.value = 0f
        try {
            val head = cell(context, 0, 0)
            val open = context.takeScreenshot("inventory-hud-before-menu")
            val behind = menuShot("inventory-hud-menu-open")
            check(pixel(context, behind, head, 0, 0) != pixel(context, open, head, 0, 0)) { "The grid still shows under an open menu" }
            closeMenu()

            inventoryHud.look.value = InventoryLook.PLATE
            waitForItem(context, "Item 0") { cell(context, 3, 0, inventory = false, gaps = 1) }
            val margin = context.placed(ID).let { block -> block.x + 2.0 * block.scale to block.y + block.height / 2.0 * block.scale }
            val plated = context.takeScreenshot("inventory-hud-plate-before-menu")
            val unplated = menuShot("inventory-hud-plate-menu-open")
            check(pixel(context, unplated, margin, 0, 0) != pixel(context, plated, margin, 0, 0)) { "The grid's plate stays under an open menu" }
            closeMenu()
            inventoryHud.look.value = InventoryLook.INVENTORY
        } finally {
            menuTheme.menuLook.value = menuLookBefore
            menuTheme.glassOpacity.value = glassBefore
        }

        inventoryHud.placement.value = HudDock.RIGHT_OF_HOTBAR
        context.waitFor { Hud.find(ID)?.dock == HudDock.RIGHT_OF_HOTBAR }
        val (width, height) = context.guiSize()
        context.waitFor { dockedRow(width, height) != null }
        val (first, last) = checkNotNull(context.computeOnClient<Pair<Pair<Int, Int>, Pair<Int, Int>>?, Nothing> { dockedRow(width, height) }) {
            "The docked grid's hotbar row is not on the screen"
        }
        check(first.first >= width / 2 + 91 + 7 && last.first < width) { "The docked grid is not right of the hotbar: $first to $last" }
        check(last.second >= height - 22) { "The docked grid's hotbar row is not level with the hotbar: $last" }
        context.takeScreenshot("inventory-hud-docked-right")

        val guiScale = context.computeOnClient<Int, Nothing> { it.window.guiScale }
        val docked = context.placed(ID)
        check(docked.scale < 1f) { "The docked grid did not shrink to the room beside the hotbar: ${docked.scale}" }
        context.openChat()
        context.point(docked.x + docked.width * docked.scale - 3.0, docked.y + 3.0, settle = 0)
        context.input.scroll(1.0)
        context.waitFor { Hud.find(ID)?.dock == HudDock.FREE }
        val grown = context.placed(ID).scale
        check(grown == docked.scale + 1f / guiScale) { "A step up from a docked grid drawn at ${docked.scale} drew it at $grown, not ${docked.scale + 1f / guiScale}" }
        context.setScreen { null }
        DsModClient.settings.hud.positions.value -= ID
    }

    private fun dockedRow(width: Int, height: Int): Pair<Pair<Int, Int>, Pair<Int, Int>>? {
        var first: Pair<Int, Int>? = null
        var last: Pair<Int, Int>? = null
        for (y in 0 until height) for (x in 0 until width) {
            val name = Hud.itemAt(x + 0.5, y + 0.5)?.hoverName?.string
            if (name == "Item 0" && first == null) first = x to y
            if (name == "Item 8") last = x to y
        }
        return if (first != null && last != null) first to last else null
    }

    private companion object {
        /** @keep Vanilla bobs a landed stack for 5 ticks, and 40 outlasts a screenshot. */
        const val POP = 40
    }
}
