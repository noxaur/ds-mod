package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.MenuTools
import io.github.noxaur.dsmod.edge.ui.KitTextField
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.fabricmc.fabric.mixin.client.gametest.input.KeyboardHandlerAccessor
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import org.lwjgl.glfw.GLFW

/**
 * Opens a menu, jumps to the search field with Ctrl+F and types into it. Items that hold every word, in their name or
 * lore, stay bright and the rest dim. While the field has focus E types instead of closing the menu, Escape leaves the
 * field and keeps the menu, the query follows into the next menu, and a right click clears it. Read the screenshots:
 * the field beside the menu, the dimmed slots, and the query in red when nothing matches.
 */
class MenuSearchTest : FabricClientGameTest {
    private fun field(context: ClientGameTestContext) = context.computeOnClient<KitTextField?, Nothing> { client ->
        val screen = client.gui.screen() as? ContainerScreen ?: return@computeOnClient null
        Screens.getWidgets(screen).filterIsInstance<KitTextField>().firstOrNull()
    }

    private fun state(context: ClientGameTestContext) = context.computeOnClient<Triple<String, Boolean, Boolean>, Nothing> { client ->
        val screen = client.gui.screen() as ContainerScreen
        val field = Screens.getWidgets(screen).filterIsInstance<KitTextField>().first()
        Triple(field.value, field.isFocused, field.visible)
    }

    private fun dims(context: ClientGameTestContext, vararg slots: Int) = context.computeOnClient<List<Boolean>, Nothing> { slots.map(MenuTools::dims) }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            ServerMenus.open(context, world, 6, "Storage") {
                it.setItem(11, ServerMenus.named(Items.DIAMOND_SWORD, "Dragon Blade").also { s -> s.set(DataComponents.LORE, ItemLore(listOf(Component.literal("LEGENDARY SWORD")))) })
                it.setItem(13, ServerMenus.named(Items.ENDER_PEARL, "Ender Pearl"))
                it.setItem(15, ServerMenus.named(Items.PRISMARINE_SHARD, "Dragon Scale"))
            }
            check(field(context)?.visible == true) { "The menu has no search field" }

            // The test input sends every key with no modifiers, so Ctrl+F goes through the game's keyboard handler here.
            context.runOnClient<Nothing> { client ->
                (client.keyboardHandler as KeyboardHandlerAccessor).invokeKeyPress(client.window.handle(), GLFW.GLFW_PRESS, KeyEvent(GLFW.GLFW_KEY_F, 0, GLFW.GLFW_MOD_CONTROL))
            }
            context.waitTicks(1)
            check(state(context).second) { "Ctrl+F did not focus the search field" }
            context.input.typeChars("dragon sword")
            context.input.pressKey(GLFW.GLFW_KEY_E)
            context.waitTicks(3)
            check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() is ContainerScreen }) { "E closed the menu while the field had focus" }
            check(state(context).first == "dragon sword") { "The field holds '${state(context).first}'" }
            check(dims(context, 11, 13, 15) == listOf(false, true, true)) { "Dimmed ${dims(context, 11, 13, 15)} for the blade, pearl and scale" }
            context.takeScreenshot("menu-search")
            context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.VANILLA }
            context.waitTicks(2)
            context.takeScreenshot("menu-search-vanilla")
            context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.CHARCOAL }

            context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
            context.waitTicks(2)
            check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() is ContainerScreen }) { "Escape closed the menu instead of leaving the field" }
            check(!state(context).second) { "Escape left the field focused" }

            ServerMenus.open(context, world, 4, "Storage Page 2") { it.setItem(10, ServerMenus.named(Items.DIAMOND_SWORD, "Dragon Blade")) }
            check(state(context).first == "dragon sword") { "The query did not follow into the next menu" }
            check(dims(context, 10) == listOf(true)) { "A blade with no lore matched 'sword' in its lore" }

            context.runOnClient<Nothing> { client ->
                val screen = client.gui.screen() as ContainerScreen
                screen.focused = Screens.getWidgets(screen).filterIsInstance<KitTextField>().first()
            }
            context.input.typeChars(" zzz")
            context.waitTicks(2)
            context.takeScreenshot("menu-search-nothing")

            val (x, y) = field(context)!!.let { it.x + 8 to it.y + 8 }
            val scale = context.computeOnClient<Double, Nothing> { it.window.guiScale.toDouble() }
            context.input.setCursorPos(x * scale, y * scale)
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT)
            context.waitTicks(2)
            check(state(context).first.isEmpty()) { "A right click left '${state(context).first}' in the field" }
            check(dims(context, 10) == listOf(false)) { "An empty query still dims" }

            context.runOnClient<Nothing> { DsModClient.settings.betterUi.enabled = false }
            try {
                context.waitTicks(2)
                check(!state(context).third) { "The field stayed with Better UI off" }
            } finally {
                context.runOnClient<Nothing> { DsModClient.settings.betterUi.enabled = true }
            }
            context.runOnClient<Nothing> { it.player!!.closeContainer() }
        }
    }
}
