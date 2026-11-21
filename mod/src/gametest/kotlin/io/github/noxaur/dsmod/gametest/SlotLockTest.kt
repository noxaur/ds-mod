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
import org.lwjgl.glfw.GLFW

/**
 * Works Slot Lock in the player's inventory with the real key and mouse. The key locks the slot under the mouse,
 * and then a click, a number key, the drop key and a double click each leave the slot as it was. Holding the key
 * from a hotbar slot to a slot above links the two, and a Shift click trades their places. Outside the menu the
 * drop key keeps a locked slot's item. Slots are the inventory's own: 0 to 8 is the hotbar and 9 the first above.
 * Read the screenshots: a link shows its frames and line under the mouse and only its padlocks at rest.
 */
class SlotLockTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val slotLock = DsModClient.feature<SlotLock>()
        val key = GLFW.GLFW_KEY_L
        val itemKey = GLFW.GLFW_KEY_K

        fun item(slot: Int) = context.computeOnClient<Pair<Any, Int>, Nothing> { it.player!!.inventory.getItem(slot).let { stack -> stack.item to stack.count } }
        fun carried() = context.computeOnClient<Int, Nothing> { it.player!!.containerMenu.carried.count }
        fun hover(slot: Int) {
            val (x, y) = context.computeOnClient<Pair<Double, Double>, Nothing> { client ->
                val screen = client.gui.screen() as AbstractContainerScreen<*>
                val at = screen.menu.slots.first { it.container is Inventory && it.containerSlot == slot }
                val scale = client.window.guiScale.toDouble()
                screen as ContainerScreenAccessor
                (screen.leftPos + at.x + 8) * scale to (screen.topPos + at.y + 8) * scale
            }
            context.input.setCursorPos(x, y)
            context.waitFor { client ->
                val hovered = ((client.gui.screen() as? ContainerScreenAccessor) ?: return@waitFor false).hoveredSlot
                hovered != null && hovered.container is Inventory && hovered.containerSlot == slot
            }
        }
        fun click(slot: Int, button: Int = 0) {
            hover(slot)
            context.input.pressMouse(button)
            context.waitTicks(3)
        }
        fun tap(slot: Int) {
            hover(slot)
            context.input.pressKey(key)
            context.waitTicks(3)
        }
        fun open() {
            context.runOnClient<Nothing> { it.gui.setScreen(InventoryScreen(it.player!!)) }
            context.waitForScreen(InventoryScreen::class.java)
            context.waitTicks(3)
        }
        /** A click on an empty slot puts the carried stack down, and the second picks it up and gathers the rest like it. */
        fun doubleClick(slot: Int) {
            hover(slot)
            context.input.pressMouse(0)
            context.input.pressMouse(0)
            context.waitTicks(5)
        }

        /** The test's mouse carries no Shift, so the click goes the way the screen sends one: to the guard, then to the game. */
        fun shiftClick(slot: Int) {
            context.runOnClient<Nothing> { client ->
                val screen = client.gui.screen() as AbstractContainerScreen<*>
                val at = screen.menu.slots.first { it.container is Inventory && it.containerSlot == slot }
                if (!SlotGuards.refuses(screen, at, 0, ContainerInput.QUICK_MOVE)) {
                    client.gameMode!!.handleContainerInput(screen.menu.containerId, at.index, 0, ContainerInput.QUICK_MOVE, client.player!!)
                }
            }
            context.waitTicks(5)
        }

        context.worldBuilder().create().use { world ->
            try {
                for ((slot, give) in listOf(
                    "hotbar.0" to "diamond_sword", "hotbar.1" to "stone 5", "inventory.0" to "diamond_pickaxe", "inventory.1" to "stone 5",
                    // An item as dragsim.co marks one that does not stack.
                    "inventory.2" to "diamond_axe[custom_data={item_data:{uuid:\"an-axe\"}}]",
                    "inventory.14" to "diamond_hoe[custom_data={item_data:{uuid:\"a-hoe\"}}]",
                )) {
                    world.server.runCommand("item replace entity @a $slot with $give")
                }
                context.waitFor { it.player!!.inventory.getItem(23).`is`(Items.DIAMOND_HOE) }
                open()

                click(0)
                check(carried() == 1) { "A click on an unlocked slot picked nothing up, so this test proves nothing" }
                click(0)

                tap(0)
                check(slotLock.locks == SlotLocks(setOf(0))) { "The key over the first hotbar slot left ${slotLock.locks}" }
                click(0)
                check(carried() == 0 && item(0).first == Items.DIAMOND_SWORD) { "A click took the locked sword" }
                click(0, 1)
                check(carried() == 0) { "A right click took the locked sword" }
                hover(10)
                context.input.pressKey(GLFW.GLFW_KEY_1)
                context.waitTicks(3)
                check(item(0).first == Items.DIAMOND_SWORD && item(10).first == Items.STONE) { "The 1 key over another slot traded with the locked slot" }
                hover(0)
                context.input.pressKey { it.keyDrop }
                context.waitTicks(3)
                check(item(0).first == Items.DIAMOND_SWORD) { "The drop key over the locked slot dropped the sword" }

                // The key is a mouse button too.
                slotLock.slotLockKey.value = 2
                click(10, 2)
                check(10 in slotLock.locks.locked) { "The middle button did not lock the slot, and left ${slotLock.locks}" }
                slotLock.slotLockKey.value = key
                click(1)
                doubleClick(12)
                check(carried() == 5 && item(10).second == 5) { "A double click gathered from the locked stack: carrying ${carried()}, and ${item(10)} is left" }
                click(1)

                // Two hotbar slots cannot be linked.
                hover(0)
                context.input.holdKey(key)
                hover(1)
                context.input.releaseKey(key)
                context.waitTicks(3)
                check(slotLock.locks.links.isEmpty() && slotLock.locks.locked == setOf(0, 10)) { "A drag between two hotbar slots left ${slotLock.locks}" }

                hover(0)
                context.input.holdKey(key)
                hover(9)
                context.takeScreenshot("slot-lock-linking")
                context.input.releaseKey(key)
                context.waitTicks(3)
                check(slotLock.locks == SlotLocks(setOf(0, 10, 9), mapOf(0 to 9))) { "Holding the key from the hotbar to the slot above left ${slotLock.locks}" }
                click(9)
                check(carried() == 0) { "A click took the linked pickaxe" }
                context.takeScreenshot("slot-lock")
                hover(20)
                context.takeScreenshot("slot-lock-rest")

                shiftClick(9)
                check(item(0).first == Items.DIAMOND_PICKAXE && item(9).first == Items.DIAMOND_SWORD) { "A Shift click did not trade the pair: ${item(0)} and ${item(9)}" }

                tap(9)
                check(slotLock.locks == SlotLocks(setOf(0, 10))) { "Unlocking one end of the link left ${slotLock.locks}" }
                tap(0)
                tap(10)
                check(slotLock.locks == SlotLocks()) { "Unlocking every slot left ${slotLock.locks}" }

                // An item lock follows the item. Slot 11 holds the axe with an ID, and stone has none.
                hover(11)
                context.input.pressKey(itemKey)
                context.waitTicks(3)
                check(slotLock.locks == SlotLocks(items = setOf("an-axe"))) { "The item key over the axe left ${slotLock.locks}" }
                click(11)
                check(carried() == 0) { "A click took the locked axe" }
                context.takeScreenshot("slot-lock-item")
                hover(11)
                context.input.pressKey(itemKey)
                context.waitTicks(3)
                click(11)
                check(carried() == 1) { "The unlocked axe did not come up" }
                click(13)
                hover(13)
                context.input.pressKey(itemKey)
                context.waitTicks(3)
                click(13)
                check(carried() == 0 && item(13).first == Items.DIAMOND_AXE) { "The lock did not follow the axe to its new slot" }
                hover(13)
                context.input.pressKey(itemKey)
                hover(1)
                context.input.pressKey(itemKey)
                context.waitTicks(3)
                check(slotLock.locks == SlotLocks()) { "The item key over stone, which stacks, left ${slotLock.locks}" }

                // A held item key locks every item it sweeps over, past the stone, and unlocks them as the first was locked.
                fun sweep(vararg slots: Int) {
                    hover(slots.first())
                    context.input.holdKey(itemKey)
                    for (slot in slots.drop(1)) hover(slot)
                    context.input.releaseKey(itemKey)
                    context.waitTicks(3)
                }
                sweep(13, 1, 23)
                check(slotLock.locks == SlotLocks(items = setOf("an-axe", "a-hoe"))) { "Sweeping the item key over the axe and the hoe left ${slotLock.locks}" }
                sweep(23, 13)
                check(slotLock.locks == SlotLocks()) { "Sweeping the item key back from a locked item left ${slotLock.locks}" }

                // A Shift click on the axe would land in the first empty hotbar slot, which is locked, so it goes to the next.
                tap(2)
                shiftClick(13)
                check(item(2).second == 0 && item(3).first == Items.DIAMOND_AXE && item(13).second == 0) {
                    "The Shift click left ${item(2)} in the locked slot, ${item(3)} beside it and ${item(13)} behind"
                }
                tap(2)
                shiftClick(3)
                check(item(3).second == 0 && item(11).first == Items.DIAMOND_AXE) { "With nothing locked a Shift click is the game's own, and left ${item(11)}" }

                click(1)
                doubleClick(12)
                check(carried() == 10) { "With nothing locked a double click gathered ${carried()}, so the check above proves nothing" }
                click(1)
                tap(1)
                context.runOnClient<Nothing> { it.gui.setScreen(null) }
                context.waitTicks(5)

                context.input.pressKey(GLFW.GLFW_KEY_2)
                context.waitTicks(3)
                context.input.pressKey { it.keyDrop }
                context.waitTicks(5)
                check(item(1).second == 10) { "The drop key dropped from the locked slot in hand, leaving ${item(1)}" }
                slotLock.feature.enabled = false
                context.input.pressKey { it.keyDrop }
                context.waitTicks(5)
                check(item(1).second == 9) { "With Slot Lock off the drop key left ${item(1)}" }
            } finally {
                slotLock.feature.enabled = true
                slotLock.slotLockKey.value = key
                slotLock.locks = SlotLocks()
                context.runOnClient<Nothing> { it.gui.setScreen(null) }
            }
        }
    }
}
