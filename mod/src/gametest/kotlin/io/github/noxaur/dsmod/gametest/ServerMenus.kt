package io.github.noxaur.dsmod.gametest

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.commands.Commands
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore

object ServerMenus {
    private val TYPES = listOf(MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3, MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6)

    fun named(item: Item, name: String): ItemStack = ItemStack(item).also { it.set(DataComponents.CUSTOM_NAME, Component.literal(name)) }

    fun lored(item: Item, name: String, lore: List<String>): ItemStack = named(item, name).also { it.set(DataComponents.LORE, ItemLore(lore.map(Component::literal))) }

    fun menu(rows: Int, title: String, goBack: Boolean = true, fill: (SimpleContainer) -> Unit = {}): SimpleMenuProvider {
        val chest = SimpleContainer(rows * 9)
        for (i in 0 until rows * 9) chest.setItem(i, named(Items.STAINED_GLASS_PANE.black(), ""))
        if (goBack) chest.setItem(rows * 9 - 6, named(Items.ARROW, "Go Back"))
        chest.setItem(rows * 9 - 5, named(Items.BARRIER, "Close"))
        fill(chest)
        return SimpleMenuProvider({ id, inventory, _ -> ChestMenu(TYPES[rows - 1], id, inventory, chest, rows) }, Component.literal(title))
    }

    fun sixRows(chest: Container, title: String) =
        SimpleMenuProvider({ id, inventory, _ -> ChestMenu.sixRows(id, inventory, chest) }, Component.literal(title))

    fun open(context: ClientGameTestContext, world: TestSingleplayerContext, rows: Int, title: String, goBack: Boolean = true, fill: (SimpleContainer) -> Unit = {}) {
        world.server.runOnServer<Nothing> { server -> server.player.openMenu(menu(rows, title, goBack, fill)) }
        shows(context, title)
    }

    fun command(world: TestSingleplayerContext, name: String, rows: Int, title: String, goBack: Boolean = true, fill: (SimpleContainer) -> Unit = {}) =
        world.server.runOnServer<Nothing> { server ->
            server.commands.dispatcher.register(Commands.literal(name).executes { it.source.playerOrException.openMenu(menu(rows, title, goBack, fill)); 1 })
        }

    fun title(client: Minecraft) = (client.gui.screen() as? AbstractContainerScreen<*>)?.title?.string

    fun shows(context: ClientGameTestContext, title: String) {
        context.waitFor { title(it) == title }
        context.waitTicks(5)
    }

    inline fun <reified W : AbstractWidget> widgets(context: ClientGameTestContext, crossinline which: (W) -> Boolean = { true }) =
        context.computeOnClient<List<W>, Nothing> { client ->
            val screen = client.gui.screen() ?: return@computeOnClient emptyList()
            Screens.getWidgets(screen).filterIsInstance<W>().filter { it.visible && which(it) }
        }

    inline fun <reified W : AbstractWidget> widget(context: ClientGameTestContext, crossinline which: (W) -> Boolean = { true }) = widgets(context, which).firstOrNull()

    fun panes(world: TestSingleplayerContext) = world.server.computeOnServer<Int, Nothing> { server ->
        server.player.inventory.count { it.`is`(Items.STAINED_GLASS_PANE.black()) }
    }
}

inline fun <reified W> Screen.widgets(): List<W> = children().filterIsInstance<W>()
