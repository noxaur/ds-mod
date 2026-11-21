package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.inventory.MenuTabs
import io.github.noxaur.dsmod.core.ui.TabEdge
import io.github.noxaur.dsmod.core.ui.TabLayout
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.TabBar
import io.github.noxaur.dsmod.edge.mixin.ContainerScreenAccessor
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitTab
import io.github.noxaur.dsmod.edge.ui.KitTextField
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.fabricmc.fabric.mixin.client.gametest.input.KeyboardHandlerAccessor
import net.minecraft.client.gui.components.ImageButton
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import org.lwjgl.glfw.GLFW

/**
 * Puts the default tabs on a menu and clicks them. A command tab sends its command, and the tab for the command that
 * opened the menu joins the panel. The search tab opens a bar toward the middle of the menu over the tabs beside it,
 * which dims the items that do not match, keeps E from closing the menu while typing, follows into the next menu and
 * closes with its button. The tabs follow the inventory when the recipe book moves it. Read the screenshots in both themes: the tabs, the selected tab, and the open bar.
 */
class MenuTabsTest : FabricClientGameTest {
    private class Shown(val name: String, val x: Int, val y: Int, val width: Int, val selected: Boolean)

    private fun tabs(context: ClientGameTestContext) = ServerMenus.widgets<KitTab>(context).map { Shown(it.message.string, it.x, it.y, it.width, it.selected) }

    private fun field(context: ClientGameTestContext) = ServerMenus.widget<KitTextField>(context)

    private fun click(context: ClientGameTestContext, tab: String) {
        val shown = tabs(context).first { it.name == tab }
        context.click(shown.x + shown.width / 2, shown.y + 10, settle = 2)
    }

    private fun screenIs(context: ClientGameTestContext, title: String) =
        context.computeOnClient<Boolean, Nothing> { (it.gui.screen() as? ContainerScreen)?.title?.string == title }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            context.runOnClient<Nothing> { DsModClient.feature<MenuTabs>().tabs.value = TabLayout.DEFAULT }
            ServerMenus.command(world, "storage", 6, "Storage", goBack = false) { chest ->
                chest.setItem(11, ServerMenus.named(Items.DIAMOND_SWORD, "Dragon Blade").also { s -> s.set(DataComponents.LORE, ItemLore(listOf(Component.literal("LEGENDARY SWORD")))) })
                chest.setItem(13, ServerMenus.named(Items.ENDER_PEARL, "Ender Pearl"))
                chest.setItem(15, ServerMenus.named(Items.PRISMARINE_SHARD, "Dragon Scale"))
            }
            try {
                ServerMenus.open(context, world, 3, "Maddox the Slayer", goBack = false)
                val shown = tabs(context)
                check(shown.map { it.name } == listOf("Storage", "Ender Chest", "Crafting Table", "Forge", "Search")) { "The tabs are ${shown.map { it.name }}" }
                check(shown.none { it.selected }) { "A tab is selected in a menu no tab opened" }
                context.takeScreenshot("menu-tabs")

                click(context, "Storage")
                ServerMenus.shows(context, "Storage")
                check(tabs(context).single { it.selected }.name == "Storage") { "The Storage tab did not join the menu it opened" }
                context.takeScreenshot("menu-tabs-selected")

                val search = tabs(context).first { it.name == "Search" }
                click(context, "Search")
                val open = field(context) ?: error("The search tab opened no bar")
                check(open.isFocused) { "The bar opened without focus" }
                check(open.x < search.x) { "The bar at the right end of the top edge did not open to the left" }
                context.input.typeChars("dragon sword")
                context.input.pressKey(GLFW.GLFW_KEY_E)
                context.waitTicks(3)
                check(screenIs(context, "Storage")) { "E closed the menu while the bar had focus" }
                check(context.computeOnClient<List<Boolean>, Nothing> { listOf(11, 13, 15).map(TabBar::dims) } == listOf(false, true, true)) { "The wrong items dimmed" }
                context.takeScreenshot("menu-tabs-search")
                context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.VANILLA }
                context.waitTicks(2)
                context.takeScreenshot("menu-tabs-search-vanilla")
                context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.CHARCOAL }

                context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
                context.waitTicks(2)
                check(screenIs(context, "Storage") && field(context)?.isFocused == false) { "Escape did not just leave the bar" }
                ServerMenus.open(context, world, 4, "Storage Page 2") { it.setItem(10, ServerMenus.named(Items.DIAMOND_SWORD, "Dragon Blade")) }
                check(field(context)?.value == "dragon sword") { "The search did not follow into the next menu" }

                val close = ServerMenus.widgets<KitButton>(context).first { it.message.string == "Close Search" }
                context.click(close.x + 8, close.y + 8, settle = 2)
                check(field(context) == null && tabs(context).any { it.name == "Search" }) { "The close button left the bar open" }

                context.runOnClient<Nothing> { client ->
                    (client.keyboardHandler as KeyboardHandlerAccessor).invokeKeyPress(client.window.handle(), GLFW.GLFW_PRESS, KeyEvent(GLFW.GLFW_KEY_F, 0, GLFW.GLFW_MOD_CONTROL))
                }
                context.waitTicks(2)
                check(field(context)?.isFocused == true) { "Ctrl+F did not open the bar" }
                context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
                context.waitTicks(2)
                check(field(context) == null) { "Escape on an empty bar left it open" }

                // On the bottom, right of the middle, the bar opens left over the command tabs.
                context.runOnClient<Nothing> { DsModClient.feature<MenuTabs>().tabs.value = TabLayout.DEFAULT.map { if (it.search) it.copy(edge = TabEdge.BOTTOM, slot = 5) else it } }
                ServerMenus.open(context, world, 4, "Storage Page 3")
                click(context, "Search")
                check(tabs(context).none { it.name in listOf("Ender Chest", "Crafting Table", "Forge") }) { "The bar did not cover the tabs beside it" }
                context.takeScreenshot("menu-tabs-search-bottom")
                context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
                context.runOnClient<Nothing> { DsModClient.feature<MenuTabs>().tabs.value = TabLayout.DEFAULT }

                context.runOnClient<Nothing> { it.player!!.closeContainer() }
                context.openInventory()
                context.waitTicks(3)
                check(tabs(context).size == 5 && tabs(context).none { it.selected }) { "The inventory's tabs are wrong" }
                context.takeScreenshot("menu-tabs-inventory")

                // The recipe book moves the inventory sideways, and the tabs must move with it.
                val before = tabs(context).map { it.x }
                val (book, leftBefore) = context.computeOnClient<Pair<Pair<Int, Int>, Int>, Nothing> { client ->
                    val screen = client.gui.screen() as InventoryScreen
                    val button = Screens.getWidgets(screen).filterIsInstance<ImageButton>().single()
                    (button.x + button.width / 2 to button.y + button.height / 2) to (screen as ContainerScreenAccessor).leftPos
                }
                context.click(book.first, book.second, settle = 2)
                val moved = context.computeOnClient<Int, Nothing> { (it.gui.screen() as ContainerScreenAccessor).leftPos } - leftBefore
                check(moved != 0) { "The recipe book did not move the inventory" }
                check(tabs(context).map { it.x } == before.map { it + moved }) { "The tabs stayed put when the recipe book moved the inventory by $moved" }
                context.takeScreenshot("menu-tabs-recipe-book")
                context.click(book.first + moved, book.second, settle = 2)
                check(tabs(context).map { it.x } == before) { "The tabs did not come back when the recipe book closed" }

                context.runOnClient<Nothing> { DsModClient.feature<MenuTabs>().feature.enabled = false }
                context.waitTicks(2)
                check(tabs(context).isEmpty()) { "The tabs stayed with Menu Tabs off" }
            } finally {
                context.runOnClient<Nothing> {
                    DsModClient.feature<MenuTabs>().feature.enabled = true
                    DsModClient.feature<MenuTabs>().tabs.value = TabLayout.DEFAULT
                }
            }
            context.setScreen { null }
        }
    }
}
