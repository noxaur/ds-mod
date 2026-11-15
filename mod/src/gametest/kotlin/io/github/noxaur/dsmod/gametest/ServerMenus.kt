package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.mixin.ContainerScreenAccessor
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Opens chest menus the way dragsim.co builds them: blank-named black panes around the items, and Go Back and Close in
 * the middle of the bottom row. The server closes the open menu before it opens the next, as dragsim.co's does.
 */
object ServerMenus {
    private val TYPES = listOf(MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3, MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6)

    fun named(item: Item, name: String): ItemStack = ItemStack(item).also { it.set(DataComponents.CUSTOM_NAME, Component.literal(name)) }

    /** Opens a menu [rows] tall titled [title], and waits until the client shows it. */
    fun open(context: ClientGameTestContext, world: TestSingleplayerContext, rows: Int, title: String, fill: (SimpleContainer) -> Unit = {}) {
        world.server.runOnServer<Nothing> { server ->
            val chest = SimpleContainer(rows * 9)
            for (i in 0 until rows * 9) chest.setItem(i, named(Items.STAINED_GLASS_PANE.black(), ""))
            chest.setItem(rows * 9 - 6, named(Items.ARROW, "Go Back"))
            chest.setItem(rows * 9 - 5, named(Items.BARRIER, "Close"))
            fill(chest)
            server.playerList.players.first().openMenu(
                SimpleMenuProvider({ id, inventory, _ -> ChestMenu(TYPES[rows - 1], id, inventory, chest, rows) }, Component.literal(title)),
            )
        }
        context.waitFor { (it.gui.screen() as? ContainerScreen)?.title?.string == title }
        context.waitTicks(5)
    }

    /** Where the open menu's slot [slot] has its top-left corner, in GUI pixels. */
    fun corner(context: ClientGameTestContext, slot: Int) = context.computeOnClient<Pair<Int, Int>, Nothing> { client ->
        val screen = client.gui.screen() as ContainerScreen
        val at = screen as ContainerScreenAccessor
        val s = screen.menu.slots[slot]
        at.leftPos + s.x to at.topPos + s.y
    }

    /** Puts the cursor over the middle of the open menu's slot [slot]. */
    fun hover(context: ClientGameTestContext, slot: Int) {
        val (x, y) = corner(context, slot)
        val scale = context.computeOnClient<Double, Nothing> { it.window.guiScale.toDouble() }
        context.input.setCursorPos((x + 8) * scale, (y + 8) * scale)
        context.waitTicks(3)
    }
}
