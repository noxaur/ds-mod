package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.ui.KitButton
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.commands.Commands
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW

/**
 * Sends a command that opens a menu, walks into a menu from it that has no Go Back, and clicks the button the mod puts
 * there. The click must send the command again and reach no slot. A menu with its own Go Back, one with an item where
 * Go Back would sit, and one opened with no command in the run get no button. Read the screenshots in both themes,
 * and the tooltip, which names the command.
 */
class BackButtonsTest : FabricClientGameTest {
    /** Where the open menu's visible Back button sits, or null when it has none. */
    private fun button(context: ClientGameTestContext) = context.computeOnClient<Pair<Int, Int>?, Nothing> { client ->
        val screen = client.gui.screen() as? ContainerScreen ?: return@computeOnClient null
        Screens.getWidgets(screen).filterIsInstance<KitButton>().firstOrNull { it.visible && it.message.string == "Go Back" }?.let { it.x to it.y }
    }

    /** Opens the menu titled [title] from the open one, as a click in a server menu does. */
    private fun from(context: ClientGameTestContext, world: TestSingleplayerContext, title: String, goBack: Boolean, fill: (net.minecraft.world.SimpleContainer) -> Unit = {}) =
        ServerMenus.open(context, world, 4, title, goBack, fill)

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            world.server.runOnServer<Nothing> { server ->
                server.commands.dispatcher.register(
                    Commands.literal("storage").executes { it.source.playerOrException.openMenu(ServerMenus.menu(6, "Storage", goBack = false)); 1 },
                )
            }
            context.runOnClient<Nothing> { it.connection!!.sendCommand("storage") }
            ServerMenus.shows(context, "Storage")
            check(button(context) == null) { "The command's own menu got a Back button" }

            from(context, world, "Backpack", goBack = true)
            check(button(context) == null) { "A menu with its own Go Back got a second one" }
            from(context, world, "Item Where Back Sits", goBack = false) { it.setItem(27 + 3, ServerMenus.named(Items.CHEST, "Backpack Slot 1")) }
            check(button(context) == null) { "The Back button covered an item" }

            from(context, world, "Backpack Slot 1", goBack = false)
            val at = button(context)
            check(at == ServerMenus.corner(context, 4 * 9 - 6)) { "The Back button is at $at, not where Go Back sits" }
            context.takeScreenshot("back-button-charcoal")
            ServerMenus.hover(context, 4 * 9 - 6)
            context.takeScreenshot("back-button-hovered")
            context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.VANILLA }
            context.waitTicks(2)
            context.takeScreenshot("back-button-vanilla")
            context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.CHARCOAL }

            context.runOnClient<Nothing> { DsModClient.settings.betterUi.backButtons = false }
            try {
                context.waitTicks(2)
                check(button(context) == null) { "The Back button stayed with Back Buttons off" }
            } finally {
                context.runOnClient<Nothing> { DsModClient.settings.betterUi.backButtons = true }
            }
            context.waitTicks(2)

            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            ServerMenus.shows(context, "Storage")
            val panes = world.server.computeOnServer<Int, Nothing> { server ->
                server.playerList.players.first().inventory.count { it.`is`(Items.STAINED_GLASS_PANE.black()) }
            }
            check(panes == 0) { "The click on Back reached the pane under it" }

            context.runOnClient<Nothing> { it.player!!.closeContainer() }
            context.waitTicks(20)
            ServerMenus.open(context, world, 6, "Maddox the Slayer", goBack = false)
            from(context, world, "Choose a Slayer", goBack = false)
            check(button(context) == null) { "A run with no command got a Back button" }
            context.runOnClient<Nothing> { it.player!!.closeContainer() }
        }
    }
}
