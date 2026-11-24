package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.menus.betterui.skyblockmenuclick.SkyBlockMenuClick
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.item.Items

class SkyBlockMenuClickTest : FabricClientGameTest {
    private val menuItem = "nether_star[custom_data={item_data:{item_id:\"skyblock_menu\"}}]"

    override fun runTest(context: ClientGameTestContext) {
        fun open() {
            context.openInventory()
            context.waitForScreen(InventoryScreen::class.java)
            context.waitTicks(3)
        }
        fun picksUp(slot: Int, why: String) {
            context.clickOwnSlot(slot)
            check(context.carried() == 1) { "A click on $why picked nothing up" }
            context.clickOwnSlot(slot)
        }

        context.worldBuilder().create().use { world ->
            ServerMenus.command(world, "sbmenu", 6, "SkyBlock Menu", goBack = false)
            world.server.runCommand("item replace entity @a hotbar.8 with $menuItem")
            world.server.runCommand("item replace entity @a inventory.0 with $menuItem")
            context.waitTicks(20)

            open()
            val sent = ClickSpy.during(context) { context.clickOwnSlot(8) }
            check(sent.isEmpty()) { "The click on the SkyBlock Menu item reached the slot: $sent" }
            ServerMenus.shows(context, "SkyBlock Menu")
            val kept = world.server.computeOnServer<Boolean, Nothing> { server -> server.player.inventory.getItem(8).`is`(Items.NETHER_STAR) }
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
