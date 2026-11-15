package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.mixin.ContainerScreenAccessor
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.HopperScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.inventory.HopperMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * Opens a server menu, the inventory and a hopper with Themed Menus and Hide Filler on, and checks the colors the kit
 * drew: the panel in the theme's color, and a filler pane's slot left as bare panel. Read the screenshots in both
 * themes: a light title on the dark panel, a dark tooltip over the hovered item, and no tooltip over filler.
 */
class ThemedMenusTest : FabricClientGameTest {
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

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            ServerMenus.open(context, world, 6, "Choose a Slayer") {
                it.setItem(11, ServerMenus.named(Items.ROTTEN_FLESH, "Revenant Horror"))
                it.setItem(13, ItemStack(Items.ENDER_PEARL))
                it.setItem(15, ServerMenus.named(Items.COBWEB, "Tarantula Broodfather"))
            }
            ServerMenus.hover(context, 13)
            val charcoal = context.takeScreenshot("themed-chest-charcoal")
            checkPanel(context, charcoal, Theme.CHARCOAL)
            val (fx, fy) = ServerMenus.corner(context, 0)
            val filler = pixel(context, charcoal, fx + 8, fy + 8)
            check(filler == Theme.CHARCOAL.panel and 0xFFFFFF) { "A filler pane's slot is %06X, not bare panel".format(filler) }

            ServerMenus.hover(context, 1)
            context.takeScreenshot("themed-chest-filler-hovered")

            theme(context, Theme.VANILLA)
            ServerMenus.hover(context, 13)
            checkPanel(context, context.takeScreenshot("themed-chest-vanilla"), Theme.VANILLA)
            theme(context, Theme.CHARCOAL)

            context.runOnClient<Nothing> { it.player!!.closeContainer() }
            context.setScreen { InventoryScreen(Minecraft.getInstance().player!!) }
            context.waitTicks(5)
            checkPanel(context, context.takeScreenshot("themed-inventory"), Theme.CHARCOAL)

            context.setScreen { null }
            world.server.runOnServer<Nothing> { server ->
                server.playerList.players.first().openMenu(SimpleMenuProvider({ id, inventory, _ -> HopperMenu(id, inventory) }, Component.literal("Hopper")))
            }
            context.waitFor { it.gui.screen() is HopperScreen }
            context.waitTicks(5)
            checkPanel(context, context.takeScreenshot("themed-hopper"), Theme.CHARCOAL)

            context.runOnClient<Nothing> { DsModClient.settings.inventory.themedMenus = false }
            try {
                context.waitTicks(2)
                val (left, top) = corner(context)
                val plain = context.takeScreenshot("themed-hopper-off")
                check(pixel(context, plain, left + 3, top + 3) != Theme.CHARCOAL.panel and 0xFFFFFF) { "The hopper stayed themed with Themed Menus off" }
            } finally {
                context.runOnClient<Nothing> { DsModClient.settings.inventory.themedMenus = true }
            }
            check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() is AbstractContainerScreen<*> })
            context.runOnClient<Nothing> { it.player!!.closeContainer() }
        }
    }
}
