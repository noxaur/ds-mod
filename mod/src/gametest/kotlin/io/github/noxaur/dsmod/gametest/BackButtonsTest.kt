package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.inventory.BackButtons
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW

/**
 * Sends a command that opens a menu, walks into a menu from it that has no Go Back, and clicks the button the mod puts
 * there. The click must send the command again and reach no slot. A menu with its own Go Back, one with an item where
 * Go Back would sit, a storage page with an empty slot there, and one opened with no command in the run get no button.
 * Read the screenshots in both themes, and the tooltip, which names the command.
 */
class BackButtonsTest : FabricClientGameTest {
    /** Where the open menu's visible Back button sits, or null when it has none. */
    private fun button(context: ClientGameTestContext) = context.goBackButton()?.let { it.x to it.y }

    /** Opens the menu titled [title] from the open one, as a click in a server menu does. */
    private fun from(context: ClientGameTestContext, world: TestSingleplayerContext, title: String, goBack: Boolean, fill: (net.minecraft.world.SimpleContainer) -> Unit = {}) =
        ServerMenus.open(context, world, 4, title, goBack, fill)

    override fun runTest(context: ClientGameTestContext) {
        context.withCommandMenu("storage", 6, "Storage", goBack = false) { world ->
            context.sendMenuCommand("storage", "Storage")
            check(button(context) == null) { "The command's own menu got a Back button" }

            from(context, world, "Backpack", goBack = true)
            check(button(context) == null) { "A menu with its own Go Back got a second one" }
            from(context, world, "Item Where Back Sits", goBack = false) { it.setItem(27 + 3, ServerMenus.named(Items.CHEST, "Backpack Slot 1")) }
            check(button(context) == null) { "The Back button covered an item" }
            from(context, world, "Storage Page (1/2)", goBack = false) { page ->
                for (i in 0 until 4 * 9) page.setItem(i, ItemStack(Items.DIRT))
                page.setItem(4 * 9 - 6, ItemStack.EMPTY)
            }
            check(button(context) == null) { "The Back button covered an empty storage slot" }
            context.takeScreenshot("back-button-storage-page")

            from(context, world, "Backpack Slot 1", goBack = false)
            val at = button(context)
            check(at == context.slotCorner(4 * 9 - 6)) { "The Back button is at $at, not where Go Back sits" }
            context.takeScreenshot("back-button-charcoal")
            val window = SettingsSteps.windowSize(context)
            context.input.resizeWindow(window.first + 200, window.second + 100)
            context.waitTicks(2)
            check(button(context) == context.slotCorner(4 * 9 - 6)) { "A resize lost the Back button or left it where Go Back was" }
            context.input.resizeWindow(window.first, window.second)
            context.waitTicks(2)
            context.hoverSlot(4 * 9 - 6)
            context.takeScreenshot("back-button-hovered")
            context.inTheme(Theme.VANILLA) { context.takeScreenshot("back-button-vanilla") }

            context.runOnClient<Nothing> { DsModClient.feature<BackButtons>().feature.enabled = false }
            try {
                context.waitTicks(2)
                check(button(context) == null) { "The Back button stayed with Back Buttons off" }
            } finally {
                context.runOnClient<Nothing> { DsModClient.feature<BackButtons>().feature.enabled = true }
            }
            context.waitTicks(2)

            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            ServerMenus.shows(context, "Storage")
            check(ServerMenus.panes(world) == 0) { "The click on Back reached the pane under it" }

            context.runOnClient<Nothing> { it.player!!.closeContainer() }
            context.waitTicks(20)
            ServerMenus.open(context, world, 6, "Maddox the Slayer", goBack = false)
            from(context, world, "Choose a Slayer", goBack = false)
            check(button(context) == null) { "A run with no command got a Back button" }
            context.runOnClient<Nothing> { it.player!!.closeContainer() }
        }
    }
}
