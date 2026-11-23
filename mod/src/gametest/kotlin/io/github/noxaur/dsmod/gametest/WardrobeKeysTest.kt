package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.inventory.MenuKeys
import io.github.noxaur.dsmod.core.feature.inventory.WardrobeKeys
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.gametest.mixin.HoveredSlotAccessor
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW

/** @keep Covers [io.github.noxaur.dsmod.edge.MenuKeyClicks]. */
class WardrobeKeysTest : FabricClientGameTest {
    private val waitTicks = (MenuKeys.WAIT_MILLIS / 50).toInt()

    private fun wardrobe(context: ClientGameTestContext, world: TestSingleplayerContext, settle: Int = 5) {
        val menu = ServerMenus.menu(6, WardrobeKeys.TITLE) {
            it.setItem(36, ServerMenus.named(Items.DYED_TERRACOTTA.lime(), "Slot 1"))
            for (slot in 37..44) it.setItem(slot, ServerMenus.named(Items.DYED_TERRACOTTA.pink(), "Slot ${slot - 35}"))
        }
        world.server.runOnServer<Nothing> { server -> server.player.openMenu(menu) }
        context.waitFor { ServerMenus.title(it) == WardrobeKeys.TITLE }
        context.waitTicks(settle)
    }

    /** @keep The test runs after the client applies the server's packets and before its tick, and the frame drawn between
     * them sets the hovered slot, so this stands in for that frame. */
    private fun hoverDrawn(context: ClientGameTestContext, slot: Int) = context.runOnClient<Nothing> { client ->
        val screen = client.gui.screen() as AbstractContainerScreen<*>
        (screen as HoveredSlotAccessor).setHoveredSlot(screen.menu.getSlot(slot))
    }

    private fun press(context: ClientGameTestContext, key: Int, ticks: Int = 2) = ClickSpy.during(context) {
        context.input.pressKey(key)
        context.waitTicks(ticks)
    }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            wardrobe(context, world, settle = 0)
            hoverDrawn(context, 0)
            check(press(context, GLFW.GLFW_KEY_3).isEmpty()) { "A press the moment the wardrobe appeared clicked at once, as a hotbar swap would" }
            context.takeScreenshot("wardrobe-keys-waiting")
            val held = ClickSpy.during(context) { context.waitTicks(waitTicks) }
            check(held == listOf(Click(38, 0, ContainerInput.PICKUP))) { "The held press did not click Slot 3 once the second was up: $held" }

            check(press(context, GLFW.GLFW_KEY_2) == listOf(Click(37, 0, ContainerInput.PICKUP))) { "A press after the second did not click Slot 2 at once" }

            world.server.runOnServer<Nothing> { server ->
                (server.player.containerMenu as ChestMenu).container.setItem(38, ServerMenus.named(Items.STAINED_GLASS_PANE.black(), "Locked"))
            }
            context.waitFor { it.player!!.containerMenu.getSlot(38).item.hoverName.string == "Locked" }
            check(press(context, GLFW.GLFW_KEY_3).isEmpty()) { "A press straight after the server locked Slot 3 clicked it from the old items" }
            context.hoverSlot(0)
            check(press(context, GLFW.GLFW_KEY_9) == listOf(Click(44, 0, ContainerInput.PICKUP))) { "A press over a slot did not click Slot 9 alone, as a hotbar swap would" }

            ServerMenus.open(context, world, 6, "Storage")
            context.hoverSlot(0)
            check(press(context, GLFW.GLFW_KEY_1).none { it.slot == 36 }) { "A wardrobe key clicked outside the wardrobe" }

            context.inTheme(Theme.VANILLA, settle = 0) {
                wardrobe(context, world)
                context.takeScreenshot("wardrobe-keys-vanilla-waiting")
            }
        }
    }
}
