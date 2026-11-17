package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.InventoryHud
import io.github.noxaur.dsmod.core.feature.general.InventoryLayout
import io.github.noxaur.dsmod.core.feature.general.InventoryLook
import io.github.noxaur.dsmod.core.feature.general.InventoryRow
import io.github.noxaur.dsmod.core.feature.inventory.MenuTheme
import io.github.noxaur.dsmod.core.ui.MenuLook
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.gametest.InventoryGrid.cell
import io.github.noxaur.dsmod.gametest.InventoryGrid.drawMicros
import io.github.noxaur.dsmod.gametest.InventoryGrid.panelColor
import io.github.noxaur.dsmod.gametest.InventoryGrid.waitForItem
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

/**
 * The Inventory look is the inventory screen as the player sees it: the theme's panel while Menu Theme is on, its
 * glass under the Glass menu look, and the inventory texture while it is off, as solid as the player sets it. The grid
 * also lies in columns, and on the HUD's plate or on nothing. Read the screenshots: each look, clear and half solid,
 * and columns.
 */
class InventoryLookTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = InventoryGrid.with(context) {
        val inventoryHud = DsModClient.feature<InventoryHud>()
        val menuTheme = DsModClient.feature<MenuTheme>()
        val hud = DsModClient.settings.hud
        val themedBefore = menuTheme.enabled
        val themeBefore = hud.theme
        val menuLookBefore = menuTheme.menuLook.value
        fun opacity(percent: Float) {
            inventoryHud.opacity.value = percent
            context.waitFor { DsModClient.game.readouts.first { it.feature.id == InventoryGrid.ID }.hud?.opacity == percent / 100f }
            context.waitTick()
        }
        inventoryHud.shownRows.value = listOf(InventoryRow.TOP, InventoryRow.MIDDLE, InventoryRow.BOTTOM, InventoryRow.HOTBAR)
        waitForItem(context, "Item 0") { cell(context, 3, 0, gaps = 1) }
        try {
            val states = listOf(
                Triple(true, Theme.CHARCOAL, MenuLook.SOLID),
                Triple(true, Theme.VANILLA, MenuLook.SOLID),
                Triple(true, Theme.CHARCOAL, MenuLook.GLASS),
                Triple(false, Theme.CHARCOAL, MenuLook.SOLID),
            )
            for ((themed, theme, menuLook) in states) {
                menuTheme.enabled = themed
                menuTheme.menuLook.value = menuLook
                hud.theme = theme
                context.waitTicks(2)
                val name = when {
                    !themed -> "vanilla-texture"
                    menuLook == MenuLook.GLASS -> "glass"
                    else -> theme.name.lowercase()
                }
                val shot = context.takeScreenshot("inventory-hud-inventory-look-$name")
                // Glass shows the world through it, so only a solid panel has one color to check.
                if (menuLook == MenuLook.SOLID) {
                    val expected = if (themed) theme.panel and 0xFFFFFF else InventoryGrid.VANILLA_PANEL
                    val panel = panelColor(context, shot)
                    check(panel == expected) { "The panel is %06X with Menu Theme %s in %s, not %06X".format(panel, themed, theme, expected) }
                    // Opacity lets the world through the panel and its slots.
                    opacity(0f)
                    val clear = panelColor(context, context.takeScreenshot("inventory-hud-inventory-look-$name-clear"))
                    check(clear != expected) { "At an opacity of 0 the panel still shows, %06X, with Menu Theme %s in %s".format(clear, themed, theme) }
                    opacity(50f)
                    val half = panelColor(context, context.takeScreenshot("inventory-hud-inventory-look-$name-half"))
                    check(half != expected && half != clear) { "At an opacity of 50 the panel is %06X, between %06X and %06X".format(half, clear, expected) }
                    opacity(100f)
                }
                println("InventoryLookTest: the HUD drew the Inventory look in ${drawMicros(context)}µs a frame, Menu Theme $themed, $theme, $menuLook")
            }
        } finally {
            menuTheme.enabled = themedBefore
            menuTheme.menuLook.value = menuLookBefore
            hud.theme = themeBefore
        }

        // In columns each row runs down, the hotbar apart to the right.
        inventoryHud.layout.value = InventoryLayout.COLUMNS
        waitForItem(context, "Item 35") { cell(context, 2, 8, columns = true) }
        check(InventoryGrid.stackAt(context, cell(context, 3, 0, gaps = 1, columns = true))?.hoverName?.string == "Item 0") { "The hotbar column is not apart to the right" }
        context.takeScreenshot("inventory-hud-columns")

        // The icon looks, for a player who wants the grid lighter than the inventory, in columns and in rows.
        for (look in listOf(InventoryLook.PLATE, InventoryLook.CLEAR)) {
            inventoryHud.layout.value = InventoryLayout.COLUMNS
            inventoryHud.look.value = look
            waitForItem(context, "Item 35") { cell(context, 2, 8, inventory = false, columns = true) }
            context.takeScreenshot("inventory-hud-${look.name.lowercase()}-columns")
            inventoryHud.layout.value = InventoryLayout.ROWS
            waitForItem(context, "Item 35") { cell(context, 2, 8, inventory = false) }
            context.takeScreenshot("inventory-hud-${look.name.lowercase()}")
            println("InventoryLookTest: the HUD drew the ${look.name} look in ${drawMicros(context)}µs a frame")
        }
    }
}
