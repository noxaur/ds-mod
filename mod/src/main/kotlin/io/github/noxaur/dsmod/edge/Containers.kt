package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.Metrics
import io.github.noxaur.dsmod.core.game.SlotChanged
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.item.ItemStack

/** @keep The server often resends the whole inventory, which swaps every stack for an equal one. */
internal fun remember(stacks: Array<ItemStack?>, counts: IntArray, i: Int, stack: ItemStack): Boolean {
    val previous = stacks[i]
    val equal = previous != null && stack.count == counts[i] && ItemStack.isSameItemSameComponents(previous, stack)
    stacks[i] = stack
    counts[i] = stack.count
    return equal
}

/** @keep A stack the client edits in place and leaves at the same count goes unnoticed. */
class ContainerPoller(private val game: Game) {
    private class Read(val menu: AbstractContainerMenu, val stacks: Array<ItemStack?>, val counts: IntArray, val items: Array<Item?>)

    private var menu: AbstractContainerMenu? = null
    private var title: Component? = null
    private var stacks = arrayOfNulls<ItemStack>(0)
    private var counts = IntArray(0)
    private var items = arrayOfNulls<Item>(0)
    private var tapped = game.tap
    private var inventory: Read? = null
    private var scheduled = false
    private var left = BUDGET_NANOS
    private val changes = ArrayList<SlotChanged>()

    fun poll(client: Minecraft) {
        read(client)
        left = BUDGET_NANOS
        scheduled = false
    }

    fun changed(client: Minecraft) {
        if (scheduled) return
        scheduled = true
        client.schedule {
            scheduled = false
            game.metrics.time("container-read", Metrics.PACKETS) { read(client) }
        }
    }

    private fun read(client: Minecraft) {
        if (!game.wants(ContainerOpened::class.java) && !game.wants(SlotChanged::class.java)) return
        val player = client.player ?: return
        val open = player.containerMenu
        // @keep Any screen can sit over the player's own inventory, so only another menu has a title of its own.
        val openTitle = if (open === player.inventoryMenu) null else (client.gui.screen() as? AbstractContainerScreen<*>)?.title
        val slots = open.slots
        if (open !== menu || openTitle != title || game.tap !== tapped) {
            val left = menu
            if (game.tap !== tapped) inventory = null else if (left is InventoryMenu) inventory = Read(left, stacks, counts, items)
            val kept = inventory?.takeIf { it.menu === open }
            menu = open
            title = openTitle
            tapped = game.tap
            stacks = kept?.stacks ?: arrayOfNulls(slots.size)
            counts = kept?.counts ?: IntArray(slots.size)
            items = kept?.items ?: arrayOfNulls(slots.size)
            game.post(System.currentTimeMillis(), ContainerOpened(open.containerId, openTitle?.toText(), slots.size, kept != null))
        }
        val start = System.nanoTime()
        val deadline = start + left
        for (i in stacks.indices) {
            val stack = slots[i].item
            if (stack === stacks[i] && stack.count == counts[i]) continue
            if (System.nanoTime() > deadline) break
            if (remember(stacks, counts, i, stack)) continue
            val item = if (stack.isEmpty) null else stack.toItem()
            if (item == items[i]) continue
            items[i] = item
            changes += SlotChanged(i, item)
        }
        if (changes.isNotEmpty()) {
            game.post(System.currentTimeMillis(), changes)
            changes.clear()
        }
        left -= System.nanoTime() - start
    }

    private companion object {
        const val BUDGET_NANOS = 1_000_000L
    }
}
