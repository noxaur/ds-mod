package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.inventory.WardrobeKeys
import io.github.noxaur.dsmod.core.ui.Theme
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW

/** @keep Covers [io.github.noxaur.dsmod.edge.Wardrobes]. */
class WardrobeKeysTest : FabricClientGameTest {
    private val waitTicks = (WardrobeKeys.WAIT_MILLIS / 50).toInt()

    private fun wardrobe(context: ClientGameTestContext, world: TestSingleplayerContext) =
        ServerMenus.open(context, world, 6, WardrobeKeys.TITLE) {
            it.setItem(36, ServerMenus.named(Items.DYED_TERRACOTTA.lime(), "Slot 1"))
            for (slot in 37..44) it.setItem(slot, ServerMenus.named(Items.DYED_TERRACOTTA.pink(), "Slot ${slot - 35}"))
        }

    private fun press(context: ClientGameTestContext, key: Int, ticks: Int = 2) = ClickSpy.during(context) {
        context.input.pressKey(key)
        context.waitTicks(ticks)
    }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            wardrobe(context, world)
            check(press(context, GLFW.GLFW_KEY_3).isEmpty()) { "A press inside the second clicked at once" }
            context.takeScreenshot("wardrobe-keys-waiting")
            val held = ClickSpy.during(context) { context.waitTicks(waitTicks) }
            check(held == listOf(Click(38, 0, ContainerInput.PICKUP))) { "The held press did not click Slot 3 once the second was up: $held" }

            check(press(context, GLFW.GLFW_KEY_2) == listOf(Click(37, 0, ContainerInput.PICKUP))) { "A press after the second did not click Slot 2 at once" }
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
