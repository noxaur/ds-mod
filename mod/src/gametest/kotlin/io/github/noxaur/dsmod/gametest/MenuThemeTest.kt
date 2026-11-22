package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.inventory.MenuTheme
import io.github.noxaur.dsmod.core.ui.MenuLook
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.mixin.ContainerScreenAccessor
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
import javax.imageio.ImageIO

/**
 * Opens a server menu, the inventory and a hopper with Menu Theme and Hide Glass Panes on, and checks the colors the kit
 * drew: the panel in the theme's color, and a filler pane's slot left as bare panel. Read the screenshots in both
 * themes: a light title on the dark panel, a dark tooltip over the hovered item, no tooltip over filler, the server's
 * Go Back and Close and the recipe book's button drawn by the kit, and Close's own tooltip over it. Under the Glass look
 * the chest and the inventory show the world undimmed through a dark sheet, with light labels, glass tabs and glass buttons.
 */
class MenuThemeTest : FabricClientGameTest {
    /** The color of the GUI pixel at [x], [y] in [shot], without its alpha. */
    private fun pixel(context: ClientGameTestContext, shot: Path, x: Int, y: Int): Int {
        val scale = context.computeOnClient<Int, Nothing> { it.window.guiScale }
        return ImageIO.read(shot.toFile()).getRGB(x * scale + scale / 2, y * scale + scale / 2) and 0xFFFFFF
    }

    private fun corner(context: ClientGameTestContext) = context.computeOnClient<Pair<Int, Int>, Nothing> { client ->
        val screen = client.gui.screen() as ContainerScreenAccessor
        screen.leftPos to screen.topPos
    }

    private fun theme(context: ClientGameTestContext, theme: Theme) {
        context.runOnClient<Nothing> { DsModClient.settings.hud.theme = theme }
        context.waitTicks(2)
    }

    private fun checkPanel(context: ClientGameTestContext, shot: Path, theme: Theme) {
        val (left, top) = corner(context)
        val panel = pixel(context, shot, left + 3, top + 3)
        check(panel == theme.panel and 0xFFFFFF) { "The panel is %06X in %s, not the theme's %06X".format(panel, shot, theme.panel and 0xFFFFFF) }
    }

    private fun look(context: ClientGameTestContext, look: MenuLook, opacity: Float = 60f) {
        context.runOnClient<Nothing> {
            DsModClient.feature<MenuTheme>().menuLook.value = look
            DsModClient.feature<MenuTheme>().glassOpacity.value = opacity
        }
        context.waitTicks(2)
    }

    private fun brightness(color: Int) = (color shr 16 and 0xFF) + (color shr 8 and 0xFF) + (color and 0xFF)

    /** Checks that glass left the world in [glass] undimmed, by a pixel off the menu that is dimmed in [solid]. */
    private fun checkUndimmed(context: ClientGameTestContext, glass: Path, solid: Path) {
        val clear = pixel(context, glass, 2, 2)
        val dimmed = pixel(context, solid, 2, 2)
        check(brightness(clear) > brightness(dimmed) + 60) { "The world behind %s is %06X, no brighter than %06X behind %s".format(glass, clear, dimmed, solid) }
    }

    /** Checks that glass at full opacity is the theme's plate, so the slider reaches solid. */
    private fun checkFullGlass(context: ClientGameTestContext, name: String, theme: Theme) {
        look(context, MenuLook.GLASS, 100f)
        val shot = context.takeScreenshot(name)
        val (left, top) = corner(context)
        val panel = pixel(context, shot, left + 3, top + 3)
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
            val filler = pixel(context, charcoal, fx + 8, fy + 8)
            check(filler == Theme.CHARCOAL.panel and 0xFFFFFF) { "A filler pane's slot is %06X, not bare panel".format(filler) }
            for (slot in listOf(6 * 9 - 6, 6 * 9 - 5)) {
                val (bx, by) = context.slotCorner(slot)
                val face = pixel(context, charcoal, bx + 3, by + 3)
                check(face == Theme.CHARCOAL.raised and 0xFFFFFF) { "The server's button in slot $slot is %06X, not a kit button".format(face) }
            }

            context.hoverSlot(1)
            context.takeScreenshot("themed-chest-filler-hovered")
            context.hoverSlot(6 * 9 - 5)
            context.takeScreenshot("themed-chest-button-hovered")
            context.runOnClient<Nothing> { DsModClient.feature<MenuTheme>().feature.enabled = false }
            try {
                context.waitTicks(2)
                val (bx, by) = context.slotCorner(6 * 9 - 6)
                val face = pixel(context, context.takeScreenshot("themed-chest-off"), bx + 3, by + 3)
                check(face != Theme.CHARCOAL.raised and 0xFFFFFF) { "The server's Go Back stayed a kit button with Menu Theme off" }
            } finally {
                context.runOnClient<Nothing> { DsModClient.feature<MenuTheme>().feature.enabled = true }
            }
            context.waitTicks(2)

            theme(context, Theme.VANILLA)
            context.hoverSlot(13)
            val vanilla = context.takeScreenshot("themed-chest-vanilla")
            checkPanel(context, vanilla, Theme.VANILLA)

            try {
                checkFullGlass(context, "glass-chest-full-vanilla", Theme.VANILLA)
                look(context, MenuLook.GLASS)
                checkUndimmed(context, context.takeScreenshot("glass-chest-vanilla"), vanilla)
                theme(context, Theme.CHARCOAL)
                val glass = context.takeScreenshot("glass-chest-charcoal")
                checkUndimmed(context, glass, charcoal)
                val (bx, by) = context.slotCorner(6 * 9 - 6)
                val face = pixel(context, glass, bx + 3, by + 3)
                check(face != Theme.CHARCOAL.raised and 0xFFFFFF) { "The server's Go Back on glass is the solid button's %06X".format(face) }
                context.hoverSlot(6 * 9 - 5)
                context.takeScreenshot("glass-chest-button-hovered")
                checkFullGlass(context, "glass-chest-full-charcoal", Theme.CHARCOAL)
                look(context, MenuLook.GLASS, 0f)
                context.takeScreenshot("glass-chest-clear")
                look(context, MenuLook.GLASS)
                // Glass is a look of Menu Theme, so with them off the chest is vanilla's, dimmed as vanilla's is.
                context.runOnClient<Nothing> { DsModClient.feature<MenuTheme>().feature.enabled = false }
                context.waitTicks(2)
                checkUndimmed(context, glass, context.takeScreenshot("glass-chest-themed-off"))
            } finally {
                context.runOnClient<Nothing> { DsModClient.feature<MenuTheme>().feature.enabled = true }
                look(context, MenuLook.SOLID)
            }
            theme(context, Theme.CHARCOAL)
            context.hoverSlot(6 * 9 - 5)
            // Only the drawing changes, so a click on Close still picks up the server's item.
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.waitTicks(3)
            val carried = context.computeOnClient<String, Nothing> { it.player!!.containerMenu.carried.hoverName.string }
            check(carried == "Close") { "The click on the server's Close reached no slot, carrying '$carried'" }
            context.runOnClient<Nothing> { it.player!!.containerMenu.carried = ItemStack.EMPTY }

            context.runOnClient<Nothing> { it.player!!.closeContainer() }
            context.openInventory()
            context.waitTicks(5)
            val solid = HashMap<Theme, Path>()
            for (theme in listOf(Theme.CHARCOAL, Theme.VANILLA)) {
                theme(context, theme)
                val shot = context.takeScreenshot("themed-inventory-${theme.name.lowercase()}")
                solid[theme] = shot
                checkPanel(context, shot, theme)
                val (x, y) = ServerMenus.widgets<ImageButton>(context).single().let { it.x to it.y }
                val face = pixel(context, shot, x + 3, y + 3)
                check(face in listOf(theme.raised, theme.hover).map { it and 0xFFFFFF }) { "The recipe book button is %06X in %s, not a kit button".format(face, shot) }
            }
            try {
                look(context, MenuLook.GLASS)
                for (theme in listOf(Theme.CHARCOAL, Theme.VANILLA)) {
                    theme(context, theme)
                    checkUndimmed(context, context.takeScreenshot("glass-inventory-${theme.name.lowercase()}"), solid.getValue(theme))
                }
            } finally {
                look(context, MenuLook.SOLID)
            }
            theme(context, Theme.CHARCOAL)

            context.setScreen { null }
            world.server.runOnServer<Nothing> { server ->
                server.playerList.players.first().openMenu(SimpleMenuProvider({ id, inventory, _ -> HopperMenu(id, inventory) }, Component.literal("Hopper")))
            }
            context.waitFor { it.gui.screen() is HopperScreen }
            context.waitTicks(5)
            checkPanel(context, context.takeScreenshot("themed-hopper"), Theme.CHARCOAL)

            context.runOnClient<Nothing> { DsModClient.feature<MenuTheme>().feature.enabled = false }
            try {
                context.waitTicks(2)
                val (left, top) = corner(context)
                val plain = context.takeScreenshot("themed-hopper-off")
                check(pixel(context, plain, left + 3, top + 3) != Theme.CHARCOAL.panel and 0xFFFFFF) { "The hopper stayed themed with Menu Theme off" }
            } finally {
                context.runOnClient<Nothing> { DsModClient.feature<MenuTheme>().feature.enabled = true }
            }
            check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() is AbstractContainerScreen<*> })
            context.runOnClient<Nothing> { it.player!!.closeContainer() }
        }
    }
}
