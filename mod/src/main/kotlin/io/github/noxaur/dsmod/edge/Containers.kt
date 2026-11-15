package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.SlotChanged
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.item.ItemStack

/**
 * Reads the client's open menu once a tick and posts what changed to [game]. A slot is looked at again only
 * when its stack is a different object or has a different count, so a quiet tick compares references.
 * A stack that the client edits in place and leaves at the same count goes unnoticed.
 * Comparing and reading items is the costly part, so one poll does that for at most [BUDGET_NANOS] and the rest of
 * a big menu follows on the next ticks, top slot first.
 * The player's own inventory keeps what was read of it while another menu is open, so going back to it reads only
 * the slots whose stack changed, and the features never see it half read.
 * The poller starts over when [Game.tap] changes, so a capture that starts later still begins with every slot.
 */
class ContainerPoller(private val game: Game) {
    private class Read(val menu: AbstractContainerMenu, val stacks: Array<ItemStack?>, val counts: IntArray, val items: Array<Item?>)

    private var menu: AbstractContainerMenu? = null
    private var title: Component? = null
    private var stacks = arrayOfNulls<ItemStack>(0)
    private var counts = IntArray(0)
    private var items = arrayOfNulls<Item>(0)
    private var tapped = game.tap
    private var inventory: Read? = null

    fun poll(client: Minecraft) {
        if (!game.wants(ContainerOpened::class.java) && !game.wants(SlotChanged::class.java)) return
        val player = client.player ?: return
        val open = player.containerMenu
        // Any screen can sit over the player's own inventory, so only another menu has a title of its own.
        val openTitle = if (open === player.inventoryMenu) null else (client.gui.screen() as? AbstractContainerScreen<*>)?.title
        val slots = open.slots
        if (open !== menu || openTitle != title || game.tap !== tapped) {
            val left = menu
            if (left is InventoryMenu) inventory = Read(left, stacks, counts, items)
            val kept = inventory?.takeIf { it.menu === open && game.tap === tapped }
            menu = open
            title = openTitle
            tapped = game.tap
            stacks = kept?.stacks ?: arrayOfNulls(slots.size)
            counts = kept?.counts ?: IntArray(slots.size)
            items = kept?.items ?: arrayOfNulls(slots.size)
            game.post(System.currentTimeMillis(), ContainerOpened(open.containerId, openTitle?.toText(), slots.size, kept != null))
        }
        val deadline = System.nanoTime() + BUDGET_NANOS
        for (i in stacks.indices) {
            val stack = slots[i].item
            val previous = stacks[i]
            if (stack === previous && stack.count == counts[i]) continue
            if (System.nanoTime() > deadline) break
            // The server often resends the whole inventory, which swaps every stack for an equal one.
            val equal = previous != null && stack.count == counts[i] && ItemStack.isSameItemSameComponents(previous, stack)
            stacks[i] = stack
            counts[i] = stack.count
            if (equal) continue
            val item = if (stack.isEmpty) null else stack.toItem()
            if (item == items[i]) continue
            items[i] = item
            game.post(System.currentTimeMillis(), SlotChanged(i, item))
        }
    }

    private companion object {
        const val BUDGET_NANOS = 1_000_000L
    }
}
