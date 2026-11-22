package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.inventory.HotdButton
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.HotdShortcut
import io.github.noxaur.dsmod.edge.ui.KitSlotButton
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW

class HotdButtonTest : FabricClientGameTest {
    private fun egg(context: ClientGameTestContext) = ServerMenus.widget<KitSlotButton>(context)?.let { it.x + 1 to it.y + 1 }

    override fun runTest(context: ClientGameTestContext) {
        context.withCommandMenu("hotd", 6, "Heart of the Dragon") { world ->
            ServerMenus.open(context, world, 6, "Dragon Sim Menu") { it.setItem(HotdShortcut.SLOT, ServerMenus.named(Items.DIAMOND_SWORD, "Your Skills")) }
            check(egg(context) == null) { "The egg covered an item" }

            ServerMenus.open(context, world, 6, "Dragon Sim Menu") { it.setItem(13, ServerMenus.named(Items.PLAYER_HEAD, "Your Dragon Sim Profile")) }
            val at = egg(context)
            check(at == context.slotCorner(HotdShortcut.SLOT)) { "The egg is at $at, not in slot ${HotdShortcut.SLOT}" }
            context.takeScreenshot("hotd-button-charcoal")
            context.hoverSlot(HotdShortcut.SLOT)
            context.takeScreenshot("hotd-button-hovered")
            context.inTheme(Theme.VANILLA) { context.takeScreenshot("hotd-button-vanilla") }

            context.runOnClient<Nothing> { DsModClient.feature<HotdButton>().feature.enabled = false }
            try {
                context.waitTicks(2)
                check(egg(context) == null) { "The egg stayed with HOTD Button off" }
            } finally {
                context.runOnClient<Nothing> { DsModClient.feature<HotdButton>().feature.enabled = true }
            }
            context.runOnClient<Nothing> { it.player!!.containerMenu.carried = ItemStack(Items.DIRT) }
            context.waitTicks(2)
            check(egg(context) == null) { "The egg stayed with an item on the cursor, whose release would land in the pane" }
            context.runOnClient<Nothing> { it.player!!.containerMenu.carried = ItemStack.EMPTY }
            context.waitTicks(2)

            val (x, y) = context.slotCorner(HotdShortcut.SLOT)
            context.point(x - 0.5, y - 0.5, settle = 3)
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT)
            ServerMenus.shows(context, "Heart of the Dragon")

            ServerMenus.open(context, world, 6, "Dragon Sim Menu")
            context.hoverSlot(HotdShortcut.SLOT)
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            ServerMenus.shows(context, "Heart of the Dragon")
            check(ServerMenus.panes(world) == 0) { "A click on the egg or its frame reached the pane under it" }
            context.runOnClient<Nothing> { it.player!!.closeContainer() }
        }
    }
}
