package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.inventory.ForgeCraft
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.ui.KitKeysButton
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
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

    private fun forging(world: TestSingleplayerContext) = world.server.runOnServer<Nothing> { server ->
        server.playerList.players.first().sendSystemMessage(Component.literal("Forging Refined Obsidian! Ready in 49m 12s."))
    }

    private fun press(context: ClientGameTestContext) = ClickSpy.during(context) {
        context.input.pressKey(GLFW.GLFW_KEY_R)
        context.waitTicks(2)
    }

    override fun runTest(context: ClientGameTestContext) {
        val feature = DsModClient.feature<ForgeCraft>()
        context.runOnClient<Nothing> { feature.key.value = GLFW.GLFW_KEY_R }
        context.worldBuilder().create().use { world ->
            carry(world, 34)
            forge(context, world, 0)
            check(ServerMenus.widget<KitKeysButton>(context) == null) { "The panel showed with nothing remembered" }
            check(press(context).isEmpty()) { "The key clicked with nothing remembered" }

            select(context, world)
            refining(context, world)
            confirm(context, world)
            forging(world)
            forge(context, world, 1)
            check(context.computeOnClient<String?, Nothing> { feature.recipe?.name } == "Refined Obsidian") { "The craft was not remembered" }
            check(ServerMenus.widget<KitKeysButton>(context) != null) { "The panel did not show at The Forge" }
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

            val keys = ServerMenus.widget<KitKeysButton>(context)!!
            context.click(keys, settle = 2)
            check(context.computeOnClient<Boolean, Nothing> { keys.recording }) { "A click on the key button did not start recording" }
            context.takeScreenshot("forge-craft-listening")
            context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
            context.waitTicks(2)
            check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() is ContainerScreen }) { "Escape closed the menu while the key button listened" }
            context.click(keys, settle = 2)
            context.input.pressKey(GLFW.GLFW_KEY_K)
            context.waitTicks(2)
            check(context.computeOnClient<Int, Nothing> { feature.key.value } == GLFW.GLFW_KEY_K) { "The key button did not bind K" }
            context.runOnClient<Nothing> { feature.key.value = GLFW.GLFW_KEY_R }

            forge(context, world, 2)
            context.hoverSlot(6 * 9)
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.waitTicks(2)
            val corner = ServerMenus.widget<KitKeysButton>(context)!!
            val onPanel = ClickSpy.during(context) { context.click(corner.x - 20, corner.y + 48, settle = 2) }
            check(onPanel.isEmpty()) { "A click on the panel reached the menu: $onPanel" }
            check(context.computeOnClient<Boolean, Nothing> { !it.player!!.containerMenu.carried.isEmpty }) { "A click on the panel dropped the carried stack" }
            context.hoverSlot(6 * 9)
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.waitTicks(2)

            forge(context, world, 10)
            check(context.computeOnClient<Boolean, Nothing> { feature.full }) { "The forge did not read as full" }
            check(press(context).isEmpty()) { "The key clicked Forge Queue with the forge full" }
            context.takeScreenshot("forge-craft-full")

            context.inTheme(Theme.VANILLA, settle = 0) {
                carry(world, 4)
                confirm(context, world, ready = false)
                context.takeScreenshot("forge-craft-vanilla-missing")
                carry(world, 34)
                forge(context, world, 3)
                context.takeScreenshot("forge-craft-vanilla")
            }
        }
    }
}
