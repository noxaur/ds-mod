package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.ui.inventoryhud.InventoryHud
import io.github.noxaur.dsmod.core.feature.ui.inventoryhud.InventoryLayout
import io.github.noxaur.dsmod.core.feature.ui.inventoryhud.InventoryLook
import io.github.noxaur.dsmod.core.feature.menus.look.menutheme.MenuTheme
import io.github.noxaur.dsmod.core.ui.MenuLook
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.gametest.InventoryGrid.cell
import io.github.noxaur.dsmod.gametest.InventoryGrid.panelColor
import io.github.noxaur.dsmod.gametest.InventoryGrid.showHotbar
import io.github.noxaur.dsmod.gametest.InventoryGrid.waitForItem
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class InventoryLookTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = InventoryGrid.with(context) {
        val inventoryHud = DsModClient.feature<InventoryHud>()
        val menuTheme = DsModClient.feature<MenuTheme>()
        val themedBefore = menuTheme.feature.enabled
        val menuLookBefore = menuTheme.menuLook.value
        fun opacity(percent: Float) {
            inventoryHud.opacity.value = percent
            context.waitFor { DsModClient.game.readouts.first { it.feature.id == InventoryGrid.ID }.hud?.opacity == percent / 100f }
            context.waitTick()
        }
        showHotbar(context)
        try {
            val states = listOf(
                Triple(true, Theme.CHARCOAL, MenuLook.SOLID),
                Triple(true, Theme.VANILLA, MenuLook.SOLID),
                Triple(true, Theme.CHARCOAL, MenuLook.GLASS),
                Triple(false, Theme.CHARCOAL, MenuLook.SOLID),
            )
            for ((themed, theme, menuLook) in states) {
                menuTheme.feature.enabled = themed
                menuTheme.menuLook.value = menuLook
                context.inTheme(theme) {
                    val name = when {
                        !themed -> "vanilla-texture"
                        menuLook == MenuLook.GLASS -> "glass"
                        else -> theme.name.lowercase()
                    }
                    val shot = context.takeScreenshot("inventory-hud-inventory-look-$name")
                    if (menuLook == MenuLook.SOLID) {
                        val expected = if (themed) theme.panel and 0xFFFFFF else InventoryGrid.VANILLA_PANEL
                        val panel = panelColor(context, shot)
                        check(panel == expected) { "The panel is %06X with Menu Theme %s in %s, not %06X".format(panel, themed, theme, expected) }
                        opacity(0f)
                        val clear = panelColor(context, context.takeScreenshot("inventory-hud-inventory-look-$name-clear"))
                        check(clear != expected) { "At an opacity of 0 the panel still shows, %06X, with Menu Theme %s in %s".format(clear, themed, theme) }
                        opacity(50f)
                        val half = panelColor(context, context.takeScreenshot("inventory-hud-inventory-look-$name-half"))
                        check(half != expected && half != clear) { "At an opacity of 50 the panel is %06X, between %06X and %06X".format(half, clear, expected) }
                        opacity(100f)
                    }
                }
            }
        } finally {
            menuTheme.feature.enabled = themedBefore
            menuTheme.menuLook.value = menuLookBefore
        }

        inventoryHud.layout.value = InventoryLayout.COLUMNS
        waitForItem(context, "Item 35") { cell(context, 2, 8, columns = true) }
        check(InventoryGrid.stackAt(context, cell(context, 3, 0, gaps = 1, columns = true))?.hoverName?.string == "Item 0") { "The hotbar column is not apart to the right" }
        context.takeScreenshot("inventory-hud-columns")

        for (look in listOf(InventoryLook.PLATE, InventoryLook.CLEAR)) {
            inventoryHud.layout.value = InventoryLayout.COLUMNS
            inventoryHud.look.value = look
            waitForItem(context, "Item 35") { cell(context, 2, 8, inventory = false, columns = true) }
            context.takeScreenshot("inventory-hud-${look.name.lowercase()}-columns")
            inventoryHud.layout.value = InventoryLayout.ROWS
            waitForItem(context, "Item 35") { cell(context, 2, 8, inventory = false) }
            context.takeScreenshot("inventory-hud-${look.name.lowercase()}")
        }
    }
}
