package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore

/**
 * Opens a chest of 54 items with long lore, which is more than one tick may spend reading. The edge must read
 * it over several ticks and still deliver every slot.
 */
class ContainerPollTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        var tick = 0
        val ticksWithSlots = HashSet<Int>()
        ClientTickEvents.START_CLIENT_TICK.register { tick++ }
        context.worldBuilder().create().use { world ->
            context.waitTicks(10)
            context.runOnClient<Nothing> {
                DsModClient.game.metrics.flush()
                DsModClient.game.tap = { _, event -> if (event is SlotChanged) ticksWithSlots += tick }
            }
            try {
                world.server.runOnServer<Nothing> { server ->
                    val chest = SimpleContainer(54)
                    for (slot in 0 until 54) {
                        chest.setItem(slot, ItemStack(Items.DIAMOND_SWORD).also { stack ->
                            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Sword $slot"))
                            stack.set(DataComponents.LORE, ItemLore((1..40).map { Component.literal("Line $it of the lore of sword $slot") }))
                        })
                    }
                    server.playerList.players.first().openMenu(
                        SimpleMenuProvider({ id, inventory, _ -> ChestMenu.sixRows(id, inventory, chest) }, Component.literal("Big Chest")),
                    )
                }
                context.waitFor { it.gui.screen() is AbstractContainerScreen<*> }
                context.waitTicks(20)
                val (read, longest) = context.computeOnClient<Pair<Int, Long>, Nothing> {
                    DsModClient.game.container.slots.take(54).count { it?.lore?.size == 40 } to
                        DsModClient.game.metrics.snapshot().timers.getValue("container-poll").maxNanos
                }
                println("ContainerPollTest: the longest poll took ${longest / 1000}µs, and slots arrived in ${ticksWithSlots.size} ticks")
                check(read == 54) { "Only $read of the 54 items arrived" }
                check(ticksWithSlots.size > 1) { "All slots were read in one tick, which took ${longest / 1000}µs" }
            } finally {
                context.runOnClient<Nothing> {
                    DsModClient.game.tap = null
                    it.player!!.closeContainer()
                }
            }
        }
    }
}
