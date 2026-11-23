package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.inventory.MenuKeys
import io.github.noxaur.dsmod.core.game.PetCard
import io.github.noxaur.dsmod.core.ui.Theme
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import org.lwjgl.glfw.GLFW

/** @keep Covers [io.github.noxaur.dsmod.edge.MenuKeyClicks] in the Pets menu, and the rarity and active marks on its pets. */
class PetsMenuTest : FabricClientGameTest {
    private val waitTicks = (MenuKeys.WAIT_MILLIS / 50).toInt()

    private fun pet(name: String, color: ChatFormatting, active: Boolean = false) = ItemStack(Items.PLAYER_HEAD).also {
        it.set(DataComponents.CUSTOM_NAME, Component.literal("[Lvl 100] ").withStyle(ChatFormatting.GRAY).append(Component.literal(name).withStyle(color)))
        val click = if (active) listOf(Component.literal("ACTIVE").withStyle(ChatFormatting.GREEN), Component.literal(PetCard.ACTIVE)) else listOf(Component.literal("Click to summon!"))
        it.set(DataComponents.LORE, ItemLore(listOf(Component.literal("LEVEL 100"), Component.empty(), Component.literal("MAX LEVEL"), Component.empty()) + click))
    }

    private fun pets(context: ClientGameTestContext, world: TestSingleplayerContext, settle: Int = 5) {
        val menu = ServerMenus.menu(6, PetCard.MENU) {
            it.setItem(4, ServerMenus.named(Items.BONE, PetCard.MENU))
            it.setItem(10, pet("Enderman", ChatFormatting.GOLD))
            it.setItem(11, pet("Wolf", ChatFormatting.GOLD))
            it.setItem(12, pet("Ender Dragon", ChatFormatting.DARK_PURPLE))
            it.setItem(13, pet("Black Cat", ChatFormatting.GOLD, active = true))
            it.setItem(14, pet("Rabbit", ChatFormatting.BLUE))
        }
        world.server.runOnServer<Nothing> { server -> server.player.openMenu(menu) }
        context.waitFor { ServerMenus.title(it) == PetCard.MENU }
        context.waitTicks(settle)
    }

    private fun press(context: ClientGameTestContext, key: Int) = ClickSpy.during(context) {
        context.input.pressKey(key)
        context.waitTicks(2)
    }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            pets(context, world, settle = 0)
            check(press(context, GLFW.GLFW_KEY_2).isEmpty()) { "A press the moment the Pets menu appeared clicked at once" }
            context.takeScreenshot("pets-menu-waiting")
            val held = ClickSpy.during(context) { context.waitTicks(waitTicks) }
            check(held == listOf(Click(11, 0, ContainerInput.PICKUP))) { "The held press did not click the second pet once the second was up: $held" }
            world.server.runOnServer<Nothing> { server ->
                server.player.containerMenu.setCarried(ItemStack.EMPTY)
                (server.player.containerMenu as ChestMenu).container.setItem(11, pet("Wolf", ChatFormatting.GOLD))
            }
            context.waitFor { !it.player!!.containerMenu.getSlot(11).item.isEmpty }

            check(press(context, GLFW.GLFW_KEY_5) == listOf(Click(14, 0, ContainerInput.PICKUP))) { "5 did not click the fifth pet" }
            check(press(context, GLFW.GLFW_KEY_6).isEmpty()) { "6 clicked though the menu has five pets" }
            context.takeScreenshot("pets-menu")

            context.inTheme(Theme.VANILLA, settle = 0) {
                pets(context, world)
                context.waitTicks(waitTicks)
                context.takeScreenshot("pets-menu-vanilla")
            }
        }
    }
}
