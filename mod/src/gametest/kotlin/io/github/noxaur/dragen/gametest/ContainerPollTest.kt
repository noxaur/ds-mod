package io.github.noxaur.dragen.gametest

import io.github.noxaur.dragen.core.game.SlotChanged
import io.github.noxaur.dragen.edge.DragenClient
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.Items

class ContainerPollTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        var tick = 0
        val ticksWithSlots = HashSet<Int>()
        ClientTickEvents.START_CLIENT_TICK.register { tick++ }
        context.worldBuilder().create().use { world ->
            context.waitTicks(10)
            context.runOnClient<Nothing> { DragenClient.game.tap = { _, event -> if (event is SlotChanged) ticksWithSlots += tick } }
            try {
                world.server.runOnServer<Nothing> { server ->
                    val chest = SimpleContainer(54)
                    for (slot in 0 until 54) {
                        chest.setItem(slot, ServerMenus.lored(Items.DIAMOND_SWORD, "Sword $slot", (1..40).map { "Line $it of the lore of sword $slot" }))
                    }
                    server.player.openMenu(ServerMenus.sixRows(chest, "Big Chest"))
                }
                context.waitFor { it.gui.screen() is AbstractContainerScreen<*> }
                context.waitTicks(20)
                val read = context.computeOnClient<Int, Nothing> { DragenClient.game.container.slots.take(54).count { it?.lore?.size == 40 } }
                check(read == 54) { "Only $read of the 54 items arrived" }
                check(ticksWithSlots.size > 1) { "All slots were read in one tick" }
            } finally {
                context.runOnClient<Nothing> {
                    DragenClient.game.tap = null
                    it.player!!.closeContainer()
                }
            }
        }
    }
}
