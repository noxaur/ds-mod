package io.github.noxaur.dsmod.edge.contribute

import io.github.noxaur.dsmod.core.contribute.ItemSightings
import io.github.noxaur.dsmod.edge.skullTexture
import io.github.noxaur.dsmod.edge.toItem
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore

/**
 * Reads the player's own inventory, its slots, armor and offhand, once a tick, and hands each look it has not seen to
 * [sightings]. Never a menu's slots: a menu can show another player's items, such as an auction's seller or a trade,
 * and the mod cannot redact those reliably. A slot is read again only when its stack is a different object or has a
 * different count, and an item is built only when its [Look] is new, so a quiet tick compares references.
 * The read stops after [BUDGET_NANOS] and goes on in the next tick, as `ContainerPoller` does.
 */
class InventorySights(private val sightings: ItemSightings) {
    private var stacks = arrayOfNulls<ItemStack>(0)
    private var counts = IntArray(0)

    /** What tells one look from another, by value, and so costs no more than reading a few components. */
    private data class Look(val entry: String, val item: Item, val glint: Boolean, val skin: String?, val texture: String?, val lore: ItemLore?)

    fun poll(client: Minecraft) {
        val inventory = client.player?.inventory ?: return
        if (stacks.size != inventory.containerSize) reset(inventory.containerSize)
        val deadline = System.nanoTime() + BUDGET_NANOS
        for (i in stacks.indices) {
            val stack = inventory.getItem(i)
            val previous = stacks[i]
            if (stack === previous && stack.count == counts[i]) continue
            // A slot skipped for want of room is not cached, so it is read again once the batch has some.
            if (sightings.full || System.nanoTime() > deadline) break
            val equal = previous != null && stack.count == counts[i] && ItemStack.isSameItemSameComponents(previous, stack)
            stacks[i] = stack
            counts[i] = stack.count
            if (equal || stack.isEmpty) continue
            look(stack)
        }
    }

    /** Forgets what was read, so that the next poll reads every slot again. */
    fun reset(size: Int = 0) {
        if (size == 0 && stacks.isEmpty()) return
        stacks = arrayOfNulls(size)
        counts = IntArray(size)
    }

    private fun look(stack: ItemStack) {
        val data = stack.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: return
        val entry = data.getCompoundOrEmpty("item_data").getString("item_id").orElse(null) ?: return
        val skin = data.getCompoundOrEmpty("extra").getCompoundOrEmpty("item_skin").getString("skinId").orElse(null)
        if (!sightings.fresh(Look(entry, stack.item, stack.hasFoil(), skin, stack.skullTexture(), stack.get(DataComponents.LORE)))) return
        sightings.see(stack.toItem())
    }

    private companion object {
        const val BUDGET_NANOS = 1_000_000L
    }
}
