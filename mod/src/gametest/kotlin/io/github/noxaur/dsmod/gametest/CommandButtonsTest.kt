package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.ui.KitButton
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.commands.Commands
import org.lwjgl.glfw.GLFW

/**
 * Lists commands in the setting, opens a menu and clicks the button for one. The server must receive that command.
 * A command too long for the column is cut short, the inventory gets the buttons too, and turning Command Buttons off
 * hides them. Read the screenshots: the buttons under the search field, beside a chest and beside the inventory.
 */
class CommandButtonsTest : FabricClientGameTest {
    /** The labels of the open screen's visible command buttons, with where each sits. */
    private fun buttons(context: ClientGameTestContext) = context.computeOnClient<Map<String, Pair<Int, Int>>, Nothing> { client ->
        val screen = client.gui.screen() as? AbstractContainerScreen<*> ?: return@computeOnClient emptyMap()
        Screens.getWidgets(screen).filterIsInstance<KitButton>().filter { it.visible && it.message.string.startsWith("/") }
            .associate { it.message.string to (it.x + it.width / 2 to it.y + 8) }
    }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            world.server.runOnServer<Nothing> { server ->
                server.commands.dispatcher.register(
                    Commands.literal("storage").executes { it.source.playerOrException.openMenu(ServerMenus.menu(6, "Storage", goBack = false)); 1 },
                )
            }
            context.runOnClient<Nothing> { DsModClient.settings.betterUi.commands = "storage, /warp dragons nest by the long way round,, storage" }
            try {
                ServerMenus.open(context, world, 3, "Maddox the Slayer", goBack = false)
                val shown = buttons(context)
                check(shown.keys.size == 2 && "/storage" in shown) { "The buttons are ${shown.keys}" }
                check(shown.keys.any { it.endsWith("...") }) { "The long command was not cut short: ${shown.keys}" }
                context.takeScreenshot("command-buttons")

                val (x, y) = shown.getValue("/storage")
                val scale = context.computeOnClient<Double, Nothing> { it.window.guiScale.toDouble() }
                context.input.setCursorPos(x * scale, y * scale)
                context.waitTicks(2)
                context.takeScreenshot("command-buttons-hovered")
                context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
                ServerMenus.shows(context, "Storage")

                context.runOnClient<Nothing> { it.player!!.closeContainer() }
                context.setScreen { InventoryScreen(Minecraft.getInstance().player!!) }
                context.waitTicks(3)
                check("/storage" in buttons(context)) { "The inventory has no command buttons" }
                context.takeScreenshot("command-buttons-inventory")

                context.runOnClient<Nothing> { DsModClient.settings.betterUi.commandButtons = false }
                context.waitTicks(2)
                check(buttons(context).isEmpty()) { "The buttons stayed with Command Buttons off" }
            } finally {
                context.runOnClient<Nothing> {
                    DsModClient.settings.betterUi.commandButtons = true
                    DsModClient.settings.betterUi.commands = "storage, ec, craft, forge"
                }
            }
            context.setScreen { null }
        }
    }
}
