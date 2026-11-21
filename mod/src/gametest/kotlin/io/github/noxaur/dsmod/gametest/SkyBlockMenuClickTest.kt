package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.inventory.SkyBlockMenuClick
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.mixin.ContainerScreenAccessor
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.commands.Commands
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.Items

class SkyBlockMenuClickTest : FabricClientGameTest {
    private val menuItem = "nether_star[custom_data={item_data:{item_id:\"skyblock_menu\"}}]"

    override fun runTest(context: ClientGameTestContext) {
        fun carried() = context.computeOnClient<Int, Nothing> { it.player!!.containerMenu.carried.count }
        fun open() {
            context.runOnClient<Nothing> { it.gui.setScreen(InventoryScreen(it.player!!)) }
            context.waitForScreen(InventoryScreen::class.java)
            context.waitTicks(3)
        }
        fun click(slot: Int) {
            val (x, y) = context.computeOnClient<Pair<Double, Double>, Nothing> { client ->
                val screen = client.gui.screen() as AbstractContainerScreen<*>
                val at = screen.menu.slots.first { it.container is Inventory && it.containerSlot == slot }
                val scale = client.window.guiScale.toDouble()
                screen as ContainerScreenAccessor
                (screen.leftPos + at.x + 8) * scale to (screen.topPos + at.y + 8) * scale
            }
            context.input.setCursorPos(x, y)
            context.waitTicks(3)
            context.input.pressMouse(0)
            context.waitTicks(3)
        }
        fun picksUp(slot: Int, why: String) {
            click(slot)
            check(carried() == 1) { "A click on $why picked nothing up" }
            click(slot)
        }

        context.worldBuilder().create().use { world ->
            world.server.runOnServer<Nothing> { server ->
                server.commands.dispatcher.register(
                    Commands.literal("sbmenu").executes { it.source.playerOrException.openMenu(ServerMenus.menu(6, "SkyBlock Menu", goBack = false)); 1 },
                )
            }
            world.server.runCommand("item replace entity @a hotbar.8 with $menuItem")
            world.server.runCommand("item replace entity @a inventory.0 with $menuItem")
            context.waitTicks(20)

            open()
            click(8)
            ServerMenus.shows(context, "SkyBlock Menu")
            check(carried() == 0) { "The click picked the SkyBlock Menu item up" }
            val kept = world.server.computeOnServer<Boolean, Nothing> { server -> server.playerList.players.first().inventory.getItem(8).`is`(Items.NETHER_STAR) }
            check(kept) { "The SkyBlock Menu item left the ninth hotbar slot" }
            context.runOnClient<Nothing> { it.player!!.closeContainer() }
            context.waitTicks(5)

            open()
            picksUp(9, "the SkyBlock Menu item outside the ninth hotbar slot")
            context.runOnClient<Nothing> { DsModClient.feature<SkyBlockMenuClick>().feature.enabled = false }
            try {
                picksUp(8, "the SkyBlock Menu item with SkyBlock Menu Click off")
            } finally {
                context.runOnClient<Nothing> { DsModClient.feature<SkyBlockMenuClick>().feature.enabled = true }
            }
            context.runOnClient<Nothing> { it.player!!.closeContainer() }
            world.server.runCommand("item replace entity @a hotbar.8 with nether_star")
            context.waitTicks(20)
            open()
            picksUp(8, "a plain nether star in the ninth hotbar slot")
            context.runOnClient<Nothing> { it.player!!.closeContainer() }
        }
    }
}
