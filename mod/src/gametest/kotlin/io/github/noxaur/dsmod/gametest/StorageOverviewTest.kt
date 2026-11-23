package io.github.noxaur.dsmod.gametest

import com.mojang.brigadier.arguments.IntegerArgumentType
import io.github.noxaur.dsmod.core.feature.inventory.StorageMode
import io.github.noxaur.dsmod.core.feature.inventory.StorageOverview
import io.github.noxaur.dsmod.core.ui.StorageLayout
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.storage.StoragePreviews
import io.github.noxaur.dsmod.edge.ui.KitTab
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback
import net.fabricmc.fabric.api.event.Event
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.concurrent.CopyOnWriteArrayList

class StorageOverviewTest : FabricClientGameTest {
    private val previewed = CopyOnWriteArrayList<String>()
    private val tooltips = CopyOnWriteArrayList<String>()

    // @keep Three pages: the first holds the blade and pearls, the second the scales, and the third is never opened.
    private fun page(n: Int) = SimpleContainer(54).also { chest ->
        chest.setItem(0, ServerMenus.named(Items.BARRIER, "Close"))
        chest.setItem(1, ServerMenus.named(Items.ARROW, "Go Back"))
        for (i in 2..8) chest.setItem(i, ServerMenus.named(Items.STAINED_GLASS_PANE.black(), ""))
        when (n) {
            1 -> {
                chest.setItem(9, ServerMenus.named(Items.DIAMOND_SWORD, "Dragon Blade"))
                chest.setItem(10, ItemStack(Items.ENDER_PEARL, 16))
            }
            2 -> chest.setItem(9, ServerMenus.named(Items.PRISMARINE_SHARD, "Dragon Scale").also { it.count = 3 })
        }
    }

    private fun commands(world: TestSingleplayerContext) {
        val pages = (1..PAGES).associateWith(::page)
        val root = SimpleContainer(54).also { chest ->
            for (n in 1..PAGES) chest.setItem(9 + n, ServerMenus.named(Items.CHEST, "Storage Page $n"))
            chest.setItem(16, ServerMenus.named(Items.ENDER_CHEST, "Ender Chest"))
        }
        val ender = SimpleContainer(36).also { it.setItem(9, ItemStack(Items.ENDER_EYE, 5)) }
        world.server.runOnServer<Nothing> { server ->
            server.commands.dispatcher.register(
                Commands.literal("storage")
                    .executes { it.source.playerOrException.openMenu(ServerMenus.sixRows(root, "Storage")); 1 }
                    .then(
                        Commands.argument("page", IntegerArgumentType.integer(1, PAGES)).executes {
                            val n = IntegerArgumentType.getInteger(it, "page")
                            it.source.playerOrException.openMenu(ServerMenus.sixRows(pages.getValue(n), "Storage Page ($n/$PAGES)"))
                            1
                        },
                    ),
            )
            server.commands.dispatcher.register(
                Commands.literal("ec").executes {
                    it.source.playerOrException.openMenu(SimpleMenuProvider({ id, inventory, _ -> ChestMenu(MenuType.GENERIC_9x4, id, inventory, ender, 4) }, Component.literal("Ender Chest")))
                    1
                },
            )
        }
    }

    private fun send(context: ClientGameTestContext, command: String, title: String) {
        context.setScreen { null }
        context.sendMenuCommand(command, title)
    }

    private fun mode(context: ClientGameTestContext, mode: StorageMode) = context.runOnClient<Nothing> { DsModClient.feature<StorageOverview>().mode.value = mode }

    private fun scale(context: ClientGameTestContext, scale: Int) {
        context.runOnClient<Nothing> {
            it.options.guiScale().set(scale)
            it.resizeGui()
        }
        context.waitTicks(3)
    }

    private fun layout(context: ClientGameTestContext) = context.computeOnClient<StorageLayout, Nothing> { client ->
        val screen = client.gui.screen() as AbstractContainerScreen<*>
        StorageLayout(screen.width, screen.height, listOf(3) + List(PAGES) { 5 })
    }

    // @keep The overview keeps its scroll from menu to menu, so it is read back from where the open page's first slot sits.
    private fun offset(context: ClientGameTestContext, layout: StorageLayout, open: Int) = layout.cellY(open, 0, 0) + 1 - context.slotCorner(9).second

    private fun cellCenter(layout: StorageLayout, page: Int, cell: Int, offset: Int) = layout.cellX(page, cell) + 9 to layout.cellY(page, cell, offset) + 9

    override fun runTest(context: ClientGameTestContext) {
        val spy = Identifier.fromNamespaceAndPath("dsmod_test", "storage_spy")
        ClientTooltipComponentCallback.EVENT.addPhaseOrdering(spy, Event.DEFAULT_PHASE)
        ClientTooltipComponentCallback.EVENT.register(spy) { data ->
            (data as? StoragePreviews.PageTooltip)?.let { previewed += it.id }
            null
        }
        ItemTooltipCallback.EVENT.register { stack, _, _, _ -> tooltips += stack.hoverName.string }
        context.worldBuilder().create().use { world ->
            commands(world)

            mode(context, StorageMode.PREVIEW)
            send(context, "storage 1", "Storage Page (1/$PAGES)")
            send(context, "storage", "Storage")
            previewed.clear()
            context.hoverSlot(10)
            check("storage:0" in previewed) { "Hovering Storage Page 1 previewed $previewed" }
            context.takeScreenshot("storage-preview")
            previewed.clear()
            tooltips.clear()
            context.input.holdKey { it.keyShift }
            context.waitTicks(3)
            context.input.releaseKey { it.keyShift }
            check(previewed.isEmpty() && "Storage Page 1" in tooltips) { "Shift did not show the server's text: $previewed, $tooltips" }
            previewed.clear()
            context.hoverSlot(12)
            check(previewed.isEmpty()) { "A page never opened previewed $previewed" }

            mode(context, StorageMode.OVERVIEW)
            val was = context.computeOnClient<Int, Nothing> { it.options.guiScale().get() }
            // @keep At scale 1 the test window fits every page side by side, so no page is scrolled out of view.
            scale(context, 1)
            send(context, "storage 2", "Storage Page (2/$PAGES)")
            val layout = layout(context)
            check(!layout.overflows) { "The pages do not fit at scale 1" }
            context.shotInBothThemes("storage-overview")
            val (slotX, _) = context.slotCorner(9)
            check(slotX == layout.cellX(2, 0) + 1) { "The open page's first slot is at x $slotX, not in its cell at ${layout.cellX(2, 0) + 1}" }
            val widgets = ServerMenus.widgets<KitTab>(context)
            val tabs = widgets.map { it.x until it.x + it.width }
            val column = layout.stemX until layout.stemX + StorageLayout.STEM_WIDTH
            check(tabs.isNotEmpty() && tabs.all { it.first in column && it.last in column }) { "The tabs at $tabs are not under the inventory at $column" }
            val search = widgets.single { it.message.string == "Search" }
            check(search.y > layout.inventoryTitleY) { "The search tab is at y ${search.y}, above the inventory at ${layout.inventoryTitleY}" }
            var offset = offset(context, layout, 2)

            tooltips.clear()
            val (bladeX, bladeY) = cellCenter(layout, 1, 0, offset)
            context.point(bladeX, bladeY, settle = 3)
            check("Dragon Blade" in tooltips) { "A remembered item's tooltip read $tooltips" }

            context.click(bladeX, bladeY, settle = 3)
            ServerMenus.shows(context, "Storage Page (1/$PAGES)")
            offset = offset(context, layout, 1)
            context.clickSlot(9)
            check(context.carried() == 1) { "The blade did not reach the cursor" }
            val (otherX, otherY) = cellCenter(layout, 2, 4, offset)
            context.click(otherX, otherY, settle = 10)
            context.checkClient("A click on another page left with an item on the cursor") { ServerMenus.title(it) == "Storage Page (1/$PAGES)" }
            context.clickSlot(9)
            check(context.carried() == 0) { "The blade did not go back" }

            scale(context, 2)
            val small = layout(context)
            check(small.overflows) { "The pages fit at scale 2, so nothing scrolls" }
            context.takeScreenshot("storage-overview-scale-2")
            val before = context.slotCorner(9).second
            context.point(small.viewX + 4, small.viewY + 4)
            context.input.scroll(-3.0)
            context.waitTicks(2)
            context.takeScreenshot("storage-overview-scrolled")
            check(context.slotCorner(9).second != before) { "The wheel did not scroll the open page" }
            scale(context, was)

            context.setScreen { null }
            send(context, "ec", "Ender Chest")
            context.takeScreenshot("storage-overview-ender-chest")
            mode(context, StorageMode.PREVIEW)
        }
    }

    private companion object {
        const val PAGES = 3
    }
}
