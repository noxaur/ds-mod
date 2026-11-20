package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.inventory.SlotLock
import io.github.noxaur.dsmod.core.inventory.SlotLocks
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.SlotGuards
import io.github.noxaur.dsmod.edge.mixin.ContainerScreenAccessor
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * A Shift click around a locked slot. One left click picks the stack up, one left click fills each unlocked slot,
 * and one left click puts back what did not fit. A crafting result is taken only when the whole output fits.
 * A held stack is parked on a real inventory slot, or the click is cancelled.
 */
class SlotLockShiftTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val feature = DsModClient.feature<SlotLock>()

        fun item(slot: Int) = context.computeOnClient<Pair<Any, Int>, Nothing> { it.player!!.inventory.getItem(slot).let { stack -> stack.item to stack.count } }
        fun carried() = context.computeOnClient<Int, Nothing> { it.player!!.containerMenu.carried.count }
        fun menuIndex(slot: Int) = context.computeOnClient<Int, Nothing> { client ->
            val screen = client.gui.screen() as AbstractContainerScreen<*>
            screen.menu.slots.first { it.container === client.player!!.inventory && it.containerSlot == slot }.index
        }
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
            hoverMenu(menuIndex(slot))
            context.input.pressMouse(0)
            context.waitTicks(2)
        }
        /** The Shift click, and every container packet from it through the next ticks, while a result may refill. */
        fun shift(index: Int, settle: Int = 2): List<Click> {
            context.runOnClient<Nothing> { ClickSpy.clear() }
            val refused = context.computeOnClient<Boolean, Nothing> { client ->
                val screen = client.gui.screen() as AbstractContainerScreen<*>
                val at = screen.menu.getSlot(index)
                val taken = SlotGuards.refuses(screen, at, 0, ContainerInput.QUICK_MOVE)
                if (!taken) client.gameMode!!.handleContainerInput(screen.menu.containerId, at.index, 0, ContainerInput.QUICK_MOVE, client.player!!)
                taken
            }
            context.waitTicks(settle)
            val clicks = context.computeOnClient<List<Click>, Nothing> { ClickSpy.snapshot() }
            check(refused) { "The Shift click was handed to vanilla: $clicks, slot9=${item(9)}, slot0=${item(0)}, slot1=${item(1)}, slot2=${item(2)}" }
            return clicks
        }
        fun shiftOwn(slot: Int, settle: Int = 2) = shift(menuIndex(slot), settle)
        fun lock(slots: Set<Int>) = context.runOnClient<Nothing> {
            feature.feature.enabled = true
            feature.locks = SlotLocks(slots)
        }
        fun left(clicks: List<Click>) {
            check(clicks.all { it.button == 0 && it.input == ContainerInput.PICKUP }) { "A click was not a left pickup: $clicks" }
        }
        /** Pickup, one left click per destination, and a put-back. Parking adds the set-down and the pick-up. */
        fun within(clicks: List<Click>, destinations: Int, parked: Boolean) {
            left(clicks)
            val cap = 2 + destinations + if (parked) 2 else 0
            check(clicks.size <= cap) { "${clicks.size} packets for $destinations destinations (parked=$parked), cap $cap: $clicks" }
        }
        fun give(world: TestSingleplayerContext, stacks: Map<Int, ItemStack>) {
            world.server.runOnServer<Nothing> { server ->
                val player = server.playerList.players.single()
                player.inventory.clearContent()
                for ((slot, stack) in stacks) player.inventory.setItem(slot, stack.copy())
                player.containerMenu.broadcastChanges()
            }
            context.waitTicks(5)
            context.runOnClient<Nothing> { client ->
                val inventory = client.player!!.inventory
                inventory.clearContent()
                for ((slot, stack) in stacks) inventory.setItem(slot, stack.copy())
                client.player!!.containerMenu.carried = ItemStack.EMPTY
            }
        }

        context.worldBuilder().create().use { world ->
            try {
                context.runOnClient<Nothing> { it.gui.setScreen(InventoryScreen(it.player!!)) }
                context.waitForScreen(InventoryScreen::class.java)

                // One left click per unlocked slot. 32 cobble, hotbar 0 holds 40, hotbar 1 locked.
                give(world, mapOf(0 to ItemStack(Items.COBBLESTONE, 40), 9 to ItemStack(Items.COBBLESTONE, 32)))
                lock(setOf(1))
                val partial = shiftOwn(9)
                val hotbar0 = menuIndex(0)
                val hotbar2 = menuIndex(2)
                val source = menuIndex(9)
                check(partial == listOf(Click(source, 0, ContainerInput.PICKUP), Click(hotbar0, 0, ContainerInput.PICKUP), Click(hotbar2, 0, ContainerInput.PICKUP))) {
                    "Partial stack clicks were $partial"
                }
                within(partial, destinations = 2, parked = false)
                check(item(1).second == 0 && item(0).second == 64 && item(2).second == 8 && item(9).second == 0) {
                    "A partial stack left ${item(1)} in the locked slot, ${item(0)} filled, ${item(2)} with the rest and ${item(9)} behind"
                }

                // The rest goes back to the source: one pickup, one destination, one put-back.
                give(world, mapOf(0 to ItemStack(Items.COBBLESTONE, 40), 9 to ItemStack(Items.COBBLESTONE, 32)))
                lock((1..8).toSet())
                val putBack = shiftOwn(9)
                check(putBack == listOf(Click(source, 0, ContainerInput.PICKUP), Click(hotbar0, 0, ContainerInput.PICKUP), Click(source, 0, ContainerInput.PICKUP))) {
                    "Put-back clicks were $putBack"
                }
                within(putBack, destinations = 1, parked = false)
                check(item(0).second == 64 && item(9).second == 8 && (1..8).all { item(it).second == 0 }) {
                    "The leftover left the source at ${item(9)} and a locked slot holding ${item(1)}"
                }

                // The held stack sits on an inventory slot that is not a destination, then comes back.
                give(world, mapOf(9 to ItemStack(Items.COBBLESTONE, 32), 10 to ItemStack(Items.DIRT, 10)))
                lock(setOf(0))
                click(10)
                check(carried() == 10) { "The dirt did not come up onto the cursor" }
                val parked = shiftOwn(9)
                val park = menuIndex(10)
                val beside = menuIndex(1)
                check(parked == listOf(Click(park, 0, ContainerInput.PICKUP), Click(source, 0, ContainerInput.PICKUP), Click(beside, 0, ContainerInput.PICKUP), Click(park, 0, ContainerInput.PICKUP))) {
                    "Parked clicks were $parked"
                }
                within(parked, destinations = 1, parked = true)
                check(item(0).second == 0 && item(1).first == Items.COBBLESTONE && item(1).second == 32 && carried() == 10) {
                    "With dirt on the cursor the cobble left ${item(0)} locked, ${item(1)} beside it, and the cursor holds ${carried()}"
                }

                // No inventory slot outside the move. An empty craft cell is not a park, and vanilla must not run.
                give(world, (2..35).associateWith { ItemStack(Items.COBBLESTONE, 64) } + mapOf(9 to ItemStack(Items.COBBLESTONE, 32)))
                lock(setOf(0))
                context.runOnClient<Nothing> { it.player!!.containerMenu.carried = ItemStack(Items.DIRT, 10) }
                val nowhere = shiftOwn(9)
                check(nowhere.isEmpty()) { "A click was sent with nowhere to park: $nowhere" }
                check(item(0).second == 0 && item(9).second == 32 && carried() == 10) {
                    "With nowhere to park the locked slot holds ${item(0)}, the source ${item(9)}, and the cursor ${carried()}"
                }
                val craftEmpty = context.computeOnClient<Boolean, Nothing> { client ->
                    val screen = client.gui.screen() as AbstractContainerScreen<*>
                    screen.menu.getSlot(1).hasItem().not()
                }
                check(craftEmpty) { "The held stack was written into the craft grid" }

                // Two logs keep crafting across ticks, one left click on the result each time the whole output fits.
                give(world, mapOf(9 to ItemStack(Items.OAK_LOG, 2)))
                click(9)
                hoverMenu(1)
                context.input.pressMouse(0)
                context.waitTicks(3)
                lock(setOf(8))
                val crafted = shift(0, settle = 8)
                left(crafted)
                check(crafted.count { it.slot == 0 } == 2 && crafted.size == 4) { "The two crafts sent $crafted" }
                within(crafted, destinations = 2, parked = false)
                check(item(8).second == 0 && item(7).first == Items.OAK_PLANKS && item(7).second == 8 && carried() == 0) {
                    "The crafts left ${item(8)} in the locked slot, ${item(7)} beside it, and the cursor holds ${carried()}"
                }

                // Room for 2 of a 4-plank result. One right click would destroy the other half, so nothing is sent.
                val half = resultThatDoesNotFit(context, world, planks = 62)
                check(half.clicks.isEmpty() && half.result == 4 && half.planks == 62 && half.log) {
                    "A result with room for half sent ${half.clicks}, left ${half.result} in the result and ${half.planks} planks"
                }

                // Room for 1, which is under half. The click is not kept pending.
                val less = resultThatDoesNotFit(context, world, planks = 63, freeAfter = true)
                check(less.clicks.isEmpty() && less.result == 4 && less.planks == 63 && less.log) {
                    "A result with room for one sent ${less.clicks}, left ${less.result} in the result and ${less.planks} planks"
                }

                // The mode or the player is missing. The click is cancelled and nothing is sent.
                give(world, mapOf(9 to ItemStack(Items.COBBLESTONE, 32)))
                lock(setOf(0))
                context.runOnClient<Nothing> { client ->
                    ClickSpy.clear()
                    val screen = client.gui.screen() as AbstractContainerScreen<*>
                    val at = screen.menu.slots.first { it.container === client.player!!.inventory && it.containerSlot == 9 }
                    val mode = client.gameMode
                    client.gameMode = null
                    try {
                        check(SlotGuards.refuses(screen, at, 0, ContainerInput.QUICK_MOVE)) { "A missing game mode was not cancelled" }
                    } finally {
                        client.gameMode = mode
                    }
                    check(ClickSpy.snapshot().isEmpty()) { "A missing game mode sent ${ClickSpy.snapshot()}" }
                    ClickSpy.clear()
                    val player = client.player
                    client.player = null
                    try {
                        check(SlotGuards.refuses(screen, at, 0, ContainerInput.QUICK_MOVE)) { "A missing player was not cancelled" }
                    } finally {
                        client.player = player
                    }
                    check(ClickSpy.snapshot().isEmpty()) { "A missing player sent ${ClickSpy.snapshot()}" }
                }
                check(item(9).second == 32 && item(0).second == 0) { "A missing mode moved the stack to ${item(0)} and left ${item(9)}" }
            } finally {
                context.runOnClient<Nothing> {
                    feature.feature.enabled = true
                    feature.locks = SlotLocks()
                    it.gui.setScreen(null)
                }
            }
        }
    }

    /** One log in the craft grid and [planks] already in the only slot that can hold more. The result is 4. */
    private fun resultThatDoesNotFit(context: ClientGameTestContext, world: TestSingleplayerContext, planks: Int, freeAfter: Boolean = false): ResultAttempt {
        world.server.runOnServer<Nothing> { server ->
            val player = server.playerList.players.single()
            player.inventory.clearContent()
            for (slot in 0..35) player.inventory.setItem(slot, ItemStack(Items.COBBLESTONE, 64))
            player.inventory.setItem(0, ItemStack(Items.OAK_PLANKS, planks))
            player.inventory.setItem(1, ItemStack.EMPTY)
            player.inventory.setItem(9, ItemStack(Items.OAK_LOG, 1))
            player.containerMenu.broadcastChanges()
        }
        context.waitTicks(5)
        context.runOnClient<Nothing> { client ->
            val inventory = client.player!!.inventory
            inventory.clearContent()
            for (slot in 0..35) inventory.setItem(slot, ItemStack(Items.COBBLESTONE, 64))
            inventory.setItem(0, ItemStack(Items.OAK_PLANKS, planks))
            inventory.setItem(1, ItemStack.EMPTY)
            inventory.setItem(9, ItemStack(Items.OAK_LOG, 1))
            client.player!!.containerMenu.carried = ItemStack.EMPTY
        }
        context.runOnClient<Nothing> {
            val feature = DsModClient.feature<SlotLock>()
            feature.locks = SlotLocks(setOf(1))
        }
        val source = context.computeOnClient<Int, Nothing> { client ->
            (client.gui.screen() as AbstractContainerScreen<*>).menu.slots.first { it.container === client.player!!.inventory && it.containerSlot == 9 }.index
        }
        hover(context, source)
        context.input.pressMouse(0)
        context.waitTicks(2)
        hover(context, 1)
        context.input.pressMouse(0)
        context.waitTicks(2)
        world.server.runOnServer<Nothing> { server ->
            server.playerList.players.single().inventory.setItem(9, ItemStack(Items.COBBLESTONE, 64))
            server.playerList.players.single().containerMenu.broadcastChanges()
        }
        context.waitTicks(3)
        context.runOnClient<Nothing> { ClickSpy.clear() }
        val refused = context.computeOnClient<Boolean, Nothing> { client ->
            val screen = client.gui.screen() as AbstractContainerScreen<*>
            SlotGuards.refuses(screen, screen.menu.getSlot(0), 0, ContainerInput.QUICK_MOVE)
        }
        context.waitTicks(10)
        if (freeAfter) {
            // Room opens after the cancel. A click still pending would now take the result by itself.
            world.server.runOnServer<Nothing> { server ->
                server.playerList.players.single().inventory.setItem(2, ItemStack.EMPTY)
                server.playerList.players.single().containerMenu.broadcastChanges()
            }
            context.waitTicks(10)
        }
        return context.computeOnClient<ResultAttempt, Nothing> { client ->
            val screen = client.gui.screen() as AbstractContainerScreen<*>
            val result = screen.menu.getSlot(0).item.count
            val have = client.player!!.inventory.getItem(0).count
            val log = screen.menu.getSlot(1).item.`is`(Items.OAK_LOG)
            ResultAttempt(ClickSpy.snapshot(), result, have, log && refused)
        }
    }

    private fun hover(context: ClientGameTestContext, index: Int) {
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
}

private data class ResultAttempt(val clicks: List<Click>, val result: Int, val planks: Int, val log: Boolean)
