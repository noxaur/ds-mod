package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.inventory.MenuTheme
import io.github.noxaur.dsmod.core.ui.MenuLook
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.components.ImageButton
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.HopperScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.inventory.HopperMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW
import java.nio.file.Path

class MenuThemeTest : FabricClientGameTest {
    private fun checkPanel(context: ClientGameTestContext, shot: Path, theme: Theme) {
        val (left, top) = context.menuCorner()
        val panel = ThemedMenus.pixel(context, shot, left + 3, top + 3)
        check(panel == theme.panel and 0xFFFFFF) { "The panel is %06X in %s, not the theme's %06X".format(panel, shot, theme.panel and 0xFFFFFF) }
    }

    private fun checkUndimmed(context: ClientGameTestContext, glass: Path, solid: Path) {
        val clear = ThemedMenus.pixel(context, glass, 2, 2)
        val dimmed = ThemedMenus.pixel(context, solid, 2, 2)
        check(brightness(clear) > brightness(dimmed) + 60) { "The world behind %s is %06X, no brighter than %06X behind %s".format(glass, clear, dimmed, solid) }
    }

    private fun checkFullGlass(context: ClientGameTestContext, name: String, theme: Theme) {
        ThemedMenus.look(context, MenuLook.GLASS, 100f)
        val shot = context.takeScreenshot(name)
        val (left, top) = context.menuCorner()
        val panel = ThemedMenus.pixel(context, shot, left + 3, top + 3)
        check(panel == theme.plate and 0xFFFFFF) { "Full glass is %06X in %s, not the theme's plate %06X".format(panel, shot, theme.plate and 0xFFFFFF) }
    }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            ServerMenus.open(context, world, 6, "Choose a Slayer") {
                it.setItem(11, ServerMenus.named(Items.ROTTEN_FLESH, "Revenant Horror"))
                it.setItem(13, ItemStack(Items.ENDER_PEARL))
                it.setItem(15, ServerMenus.named(Items.COBWEB, "Tarantula Broodfather"))
            }
            context.hoverSlot(13)
            val charcoal = context.takeScreenshot("themed-chest-charcoal")
            checkPanel(context, charcoal, Theme.CHARCOAL)
            val (fx, fy) = context.slotCorner(0)
            val filler = ThemedMenus.pixel(context, charcoal, fx + 8, fy + 8)
            check(filler == Theme.CHARCOAL.panel and 0xFFFFFF) { "A filler pane's slot is %06X, not bare panel".format(filler) }
            for (slot in listOf(6 * 9 - 6, 6 * 9 - 5)) {
                val (bx, by) = context.slotCorner(slot)
                val face = ThemedMenus.pixel(context, charcoal, bx + 3, by + 3)
                check(face == Theme.CHARCOAL.raised and 0xFFFFFF) { "The server's button in slot $slot is %06X, not a kit button".format(face) }
            }

            context.hoverSlot(1)
            context.takeScreenshot("themed-chest-filler-hovered")
            context.hoverSlot(6 * 9 - 5)
            context.takeScreenshot("themed-chest-button-hovered")
            ThemedMenus.themeOff(context) {
                val (bx, by) = context.slotCorner(6 * 9 - 6)
                val face = ThemedMenus.pixel(context, context.takeScreenshot("themed-chest-off"), bx + 3, by + 3)
                check(face != Theme.CHARCOAL.raised and 0xFFFFFF) { "The server's Go Back stayed a kit button with Menu Theme off" }
            }
            context.waitTicks(2)

            val vanilla = context.inTheme(Theme.VANILLA) {
                context.hoverSlot(13)
                context.takeScreenshot("themed-chest-vanilla").also { checkPanel(context, it, Theme.VANILLA) }
            }

            try {
                context.inTheme(Theme.VANILLA) {
                    checkFullGlass(context, "glass-chest-full-vanilla", Theme.VANILLA)
                    ThemedMenus.look(context, MenuLook.GLASS)
                    checkUndimmed(context, context.takeScreenshot("glass-chest-vanilla"), vanilla)
                }
                val glass = context.takeScreenshot("glass-chest-charcoal")
                checkUndimmed(context, glass, charcoal)
                val (bx, by) = context.slotCorner(6 * 9 - 6)
                val face = ThemedMenus.pixel(context, glass, bx + 3, by + 3)
                check(face != Theme.CHARCOAL.raised and 0xFFFFFF) { "The server's Go Back on glass is the solid button's %06X".format(face) }
                context.hoverSlot(6 * 9 - 5)
                context.takeScreenshot("glass-chest-button-hovered")
                checkFullGlass(context, "glass-chest-full-charcoal", Theme.CHARCOAL)
                ThemedMenus.look(context, MenuLook.GLASS, 0f)
                context.takeScreenshot("glass-chest-clear")
                ThemedMenus.look(context, MenuLook.GLASS)
                context.runOnClient<Nothing> { DsModClient.feature<MenuTheme>().feature.enabled = false }
                context.waitTicks(2)
                checkUndimmed(context, glass, context.takeScreenshot("glass-chest-themed-off"))
            } finally {
                context.runOnClient<Nothing> { DsModClient.feature<MenuTheme>().feature.enabled = true }
                ThemedMenus.look(context, MenuLook.SOLID)
            }
            context.hoverSlot(6 * 9 - 5)
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.waitTicks(3)
            val carried = context.computeOnClient<String, Nothing> { it.player!!.containerMenu.carried.hoverName.string }
            check(carried == "Close") { "The click on the server's Close reached no slot, carrying '$carried'" }
            context.runOnClient<Nothing> { it.player!!.containerMenu.carried = ItemStack.EMPTY }

            context.runOnClient<Nothing> { it.player!!.closeContainer() }
            context.openInventory()
            context.waitTicks(5)
            val solid = HashMap<Theme, Path>()
            for (theme in listOf(Theme.CHARCOAL, Theme.VANILLA)) context.inTheme(theme) {
                val shot = context.takeScreenshot("themed-inventory-${theme.name.lowercase()}")
                solid[theme] = shot
                checkPanel(context, shot, theme)
                val book = ServerMenus.widgets<ImageButton>(context).single()
                val face = ThemedMenus.pixel(context, shot, book.x + 3, book.y + 3)
                check(face in listOf(theme.raised, theme.hover).map { it and 0xFFFFFF }) { "The recipe book button is %06X in %s, not a kit button".format(face, shot) }
            }
            try {
                ThemedMenus.look(context, MenuLook.GLASS)
                for (theme in listOf(Theme.CHARCOAL, Theme.VANILLA)) context.inTheme(theme) {
                    checkUndimmed(context, context.takeScreenshot("glass-inventory-${theme.name.lowercase()}"), solid.getValue(theme))
                }
            } finally {
                ThemedMenus.look(context, MenuLook.SOLID)
            }

            context.setScreen { null }
            world.server.runOnServer<Nothing> { server ->
                server.player.openMenu(SimpleMenuProvider({ id, inventory, _ -> HopperMenu(id, inventory) }, Component.literal("Hopper")))
            }
            context.waitFor { it.gui.screen() is HopperScreen }
            context.waitTicks(5)
            checkPanel(context, context.takeScreenshot("themed-hopper"), Theme.CHARCOAL)

            ThemedMenus.checkOff(context, "hopper", "hopper")
            check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() is AbstractContainerScreen<*> })
            context.runOnClient<Nothing> { it.player!!.closeContainer() }
        }
    }
}
