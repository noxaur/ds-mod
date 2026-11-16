package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.inventory.SlotLock
import io.github.noxaur.dsmod.core.inventory.SlotLocks
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.SlotGuards
import io.github.noxaur.dsmod.edge.mixin.ContainerScreenAccessor
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.Items

/**
 * A Shift click around a locked slot. These cases sit beside [SlotLockTest] because that test is already at the
 * time cap. A partial stack fills every unlocked slot that has room, a stack on the cursor stays on the cursor,
 * and a crafting result keeps going until the grid is empty.
 */
class SlotLockShiftTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val feature = DsModClient.game.readouts.single { it.feature.id == "slot-lock" }.feature as SlotLock

        fun item(slot: Int) = context.computeOnClient<Pair<Any, Int>, Nothing> { it.player!!.inventory.getItem(slot).let { stack -> stack.item to stack.count } }
        fun carried() = context.computeOnClient<Int, Nothing> { it.player!!.containerMenu.carried.count }
        fun hoverMenu(index: Int) {
            val (x, y) = context.computeOnClient<Pair<Double, Double>, Nothing> { client ->
                val screen = client.gui.screen() as AbstractContainerScreen<*>
                val at = screen.menu.getSlot(index)
                val scale = client.window.guiScale.toDouble()
                screen as ContainerScreenAccessor
                (screen.leftPos + at.x + 8) * scale to (screen.topPos + at.y + 8) * scale
            }
            context.input.setCursorPos(x, y)
            context.waitTicks(2)
        }
        fun click(slot: Int) {
            val index = context.computeOnClient<Int, Nothing> { client ->
                val screen = client.gui.screen() as AbstractContainerScreen<*>
                screen.menu.slots.first { it.container is Inventory && it.containerSlot == slot }.index
            }
            hoverMenu(index)
            context.input.pressMouse(0)
            context.waitTicks(2)
        }
        fun shift(index: Int) {
            context.runOnClient<Nothing> { client ->
                val screen = client.gui.screen() as AbstractContainerScreen<*>
                val at = screen.menu.getSlot(index)
                if (!SlotGuards.refuses(screen, at, 0, ContainerInput.QUICK_MOVE)) {
                    client.gameMode!!.handleContainerInput(screen.menu.containerId, at.index, 0, ContainerInput.QUICK_MOVE, client.player!!)
                }
            }
            context.waitTicks(8)
        }
        fun shiftOwn(slot: Int) = shift(context.computeOnClient<Int, Nothing> { client ->
            val screen = client.gui.screen() as AbstractContainerScreen<*>
            screen.menu.slots.first { it.container is Inventory && it.containerSlot == slot }.index
        })
        fun lock(slots: Set<Int>) = context.runOnClient<Nothing> {
            feature.enabled = true
            feature.locks = SlotLocks(slots)
        }

        context.worldBuilder().create().use { world ->
            try {
                context.runOnClient<Nothing> { it.gui.setScreen(InventoryScreen(it.player!!)) }
                context.waitForScreen(InventoryScreen::class.java)

                world.server.runCommand("item replace entity @a hotbar.0 with cobblestone 40")
                world.server.runCommand("item replace entity @a inventory.0 with cobblestone 32")
                context.waitTicks(5)
                lock(setOf(1))
                shiftOwn(9)
                check(item(1).second == 0 && item(0).second == 64 && item(2).second == 8 && item(9).second == 0) {
                    "A partial stack left ${item(1)} in the locked slot, ${item(0)} filled, ${item(2)} with the rest and ${item(9)} behind"
                }

                world.server.runCommand("clear @a")
                world.server.runCommand("item replace entity @a inventory.0 with cobblestone 32")
                world.server.runCommand("item replace entity @a inventory.1 with dirt 10")
                context.waitTicks(5)
                lock(setOf(0))
                click(10)
                check(carried() == 10) { "The dirt did not come up onto the cursor" }
                shiftOwn(9)
                check(item(0).second == 0 && item(1).first == Items.COBBLESTONE && item(1).second == 32 && carried() == 10) {
                    "With dirt on the cursor the cobble left ${item(0)} locked, ${item(1)} beside it, and the cursor holds ${carried()}"
                }

                world.server.runCommand("clear @a")
                world.server.runCommand("item replace entity @a inventory.0 with oak_log 2")
                context.waitTicks(5)
                click(9)
                hoverMenu(1)
                context.input.pressMouse(0)
                context.waitTicks(3)
                lock(setOf(8))
                shift(0)
                check(item(8).second == 0 && item(7).first == Items.OAK_PLANKS && item(7).second == 8 && carried() == 0) {
                    "The crafts left ${item(8)} in the locked slot, ${item(7)} beside it, and the cursor holds ${carried()}"
                }
            } finally {
                context.runOnClient<Nothing> {
                    feature.enabled = true
                    feature.locks = SlotLocks()
                    it.gui.setScreen(null)
                }
            }
        }
    }
}
