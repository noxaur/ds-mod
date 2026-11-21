package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.inventory.ForgeCraft
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitKeysButton
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import org.lwjgl.glfw.GLFW

class ForgeCraftTest : FabricClientGameTest {
    private fun named(item: net.minecraft.world.item.Item, name: String, color: Int = 0xFFFFFF, count: Int = 1, vararg lore: String): ItemStack =
        ItemStack(item, count).also {
            it.set(DataComponents.CUSTOM_NAME, Component.literal(name).withColor(color))
            if (lore.isNotEmpty()) it.set(DataComponents.LORE, ItemLore(lore.map { line -> Component.literal(line) }))
        }

    private val refined get() = named(Items.OBSIDIAN, "Refined Obsidian", 0x5555FF, 1, "RARE", "", "Items Required", "Enchanted Obsidian x16", "", "Duration: 49m 12s")

    private fun forge(context: ClientGameTestContext, world: TestSingleplayerContext, queued: Int) =
        ServerMenus.open(context, world, 6, ForgeCraft.FORGE, goBack = false) {
            it.setItem(22, named(Items.HOPPER, "Forge Queue ($queued/10)", 0xFFAA00, 1, "Click to select another recipe!"))
        }

    private fun select(context: ClientGameTestContext, world: TestSingleplayerContext) =
        ServerMenus.open(context, world, 6, ForgeCraft.SELECT) { it.setItem(11, named(Items.END_STONE_BRICKS, "Refining", 0x55FF55, 1, "Click to browse!")) }

    private fun refining(context: ClientGameTestContext, world: TestSingleplayerContext) =
        ServerMenus.open(context, world, 6, "Refining") { it.setItem(11, refined) }

    private fun confirm(context: ClientGameTestContext, world: TestSingleplayerContext, ready: Boolean = true) =
        ServerMenus.open(context, world, 4, ForgeCraft.CONFIRM) {
            it.setItem(4, refined)
            it.setItem(13, named(Items.OBSIDIAN, "Enchanted Obsidian", 0x55FF55, 16))
            it.setItem(20, named(if (ready) Items.DYED_TERRACOTTA.green() else Items.DYED_TERRACOTTA.gray(), "Confirm", if (ready) 0x55FF55 else 0xAAAAAA))
        }

    private fun carry(world: TestSingleplayerContext, count: Int) = world.server.runOnServer<Nothing> { server ->
        val inventory = server.playerList.players.first().inventory
        inventory.clearContent()
        inventory.setItem(9, named(Items.OBSIDIAN, "Enchanted Obsidian", 0x55FF55, minOf(count, 64)))
    }

    private fun queued(world: TestSingleplayerContext) = world.server.runOnServer<Nothing> { server ->
        server.playerList.players.first().sendSystemMessage(Component.literal("Queued Refined Obsidian! It starts automatically when a Forge slot finishes."))
    }

    private inline fun <reified W : AbstractWidget> widget(context: ClientGameTestContext, crossinline which: (W) -> Boolean = { true }) =
        context.computeOnClient<W?, Nothing> { client ->
            val screen = client.gui.screen() as? ContainerScreen ?: return@computeOnClient null
            Screens.getWidgets(screen).filterIsInstance<W>().firstOrNull { it.visible && which(it) }
        }

    private fun press(context: ClientGameTestContext): List<Click> {
        ClickSpy.clear()
        context.input.pressKey(GLFW.GLFW_KEY_R)
        context.waitTicks(2)
        return ClickSpy.snapshot()
    }

    private fun clickAt(context: ClientGameTestContext, widget: AbstractWidget, button: Int = GLFW.GLFW_MOUSE_BUTTON_LEFT) {
        val scale = context.computeOnClient<Double, Nothing> { it.window.guiScale.toDouble() }
        context.input.setCursorPos((widget.x + widget.width / 2) * scale, (widget.y + widget.height / 2) * scale)
        context.waitTicks(2)
        context.input.pressMouse(button)
        context.waitTicks(2)
    }

    override fun runTest(context: ClientGameTestContext) {
        val feature = DsModClient.feature<ForgeCraft>()
        context.runOnClient<Nothing> {
            feature.key.value = GLFW.GLFW_KEY_R
            feature.amount = 3
        }
        context.worldBuilder().create().use { world ->
            carry(world, 34)
            forge(context, world, 0)
            check(widget<KitKeysButton>(context) == null) { "The panel showed with nothing remembered" }
            check(press(context).isEmpty()) { "The key clicked with nothing remembered" }

            select(context, world)
            refining(context, world)
            confirm(context, world)
            queued(world)
            forge(context, world, 1)
            check(context.computeOnClient<String?, Nothing> { feature.recipe?.name } == "Refined Obsidian") { "The craft was not remembered" }
            check(widget<KitKeysButton>(context) != null) { "The panel did not show at The Forge" }
            context.takeScreenshot("forge-craft-forge")

            check(press(context) == listOf(Click(22, 0, ContainerInput.PICKUP))) { "ForgeCrafts did not click Forge Queue once" }
            check(press(context).isEmpty()) { "A second press clicked before the next menu opened" }
            select(context, world)
            check(press(context) == listOf(Click(11, 0, ContainerInput.PICKUP))) { "The key did not click Refining" }
            refining(context, world)
            context.takeScreenshot("forge-craft-refining")
            check(press(context) == listOf(Click(11, 0, ContainerInput.PICKUP))) { "The key did not click Refined Obsidian" }

            carry(world, 4)
            confirm(context, world, ready = false)
            context.takeScreenshot("forge-craft-missing")
            check(press(context).isEmpty()) { "The key clicked gray Confirm" }
            carry(world, 34)
            confirm(context, world)
            check(press(context) == listOf(Click(20, 0, ContainerInput.PICKUP))) { "The key did not click Confirm" }

            val keys = widget<KitKeysButton>(context)!!
            clickAt(context, keys)
            check(context.computeOnClient<Boolean, Nothing> { keys.recording }) { "A click on the key button did not start recording" }
            context.takeScreenshot("forge-craft-listening")
            context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
            context.waitTicks(2)
            check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() is ContainerScreen }) { "Escape closed the menu while the key button listened" }
            clickAt(context, keys)
            context.input.pressKey(GLFW.GLFW_KEY_K)
            context.waitTicks(2)
            check(context.computeOnClient<Int, Nothing> { feature.key.value } == GLFW.GLFW_KEY_K) { "The key button did not bind K" }
            context.runOnClient<Nothing> { feature.key.value = GLFW.GLFW_KEY_R }

            forge(context, world, 2)
            val plus = widget<KitButton>(context) { it.message.string == "+" }!!
            clickAt(context, plus)
            check(context.computeOnClient<Int, Nothing> { feature.amount } == 4) { "+ did not raise the amount" }
            context.input.scroll(-1.0)
            context.waitTicks(2)
            check(context.computeOnClient<Int, Nothing> { feature.amount } == 3) { "The wheel over the amount did not lower it" }

            ServerMenus.hover(context, 6 * 9)
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.waitTicks(2)
            val scale = context.computeOnClient<Double, Nothing> { it.window.guiScale.toDouble() }
            val corner = widget<KitKeysButton>(context)!!
            ClickSpy.clear()
            context.input.setCursorPos((corner.x - 20) * scale, (corner.y + 48) * scale)
            context.waitTicks(2)
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.waitTicks(2)
            check(ClickSpy.snapshot().isEmpty()) { "A click on the panel reached the menu: ${ClickSpy.snapshot()}" }
            check(context.computeOnClient<Boolean, Nothing> { !it.player!!.containerMenu.carried.isEmpty }) { "A click on the panel dropped the carried stack" }
            ServerMenus.hover(context, 6 * 9)
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.waitTicks(2)

            context.runOnClient<Nothing> { feature.amount = 1 }
            forge(context, world, 3)
            queued(world)
            context.waitTicks(3)
            check(context.computeOnClient<Boolean, Nothing> { feature.done }) { "The amount was not reached" }
            check(press(context).isEmpty()) { "The key clicked after the amount was reached" }
            context.takeScreenshot("forge-craft-done")

            context.runOnClient<Nothing> {
                feature.amount = 5
                DsModClient.settings.hud.theme = Theme.VANILLA
            }
            carry(world, 4)
            confirm(context, world, ready = false)
            context.takeScreenshot("forge-craft-vanilla-missing")
            carry(world, 34)
            forge(context, world, 3)
            context.takeScreenshot("forge-craft-vanilla")
            context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.CHARCOAL }
        }
    }
}
