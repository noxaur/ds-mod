package io.github.noxaur.dragen.edge.contribute

import io.github.noxaur.dragen.core.contribute.ItemSightings
import io.github.noxaur.dragen.edge.remember
import io.github.noxaur.dragen.edge.itemId
import io.github.noxaur.dragen.edge.skinId
import io.github.noxaur.dragen.edge.skullTexture
import io.github.noxaur.dragen.edge.toItem
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore

/** @keep Never a menu's slots: a menu can show another player's items, such as an auction's seller or a trade, and the mod cannot redact those reliably. */
class InventorySights(private val sightings: ItemSightings) {
    private var stacks = arrayOfNulls<ItemStack>(0)
    private var counts = IntArray(0)

    private data class Look(val entry: String, val item: Item, val glint: Boolean, val skin: String?, val texture: String?, val lore: ItemLore?)

    fun poll(client: Minecraft) {
        val inventory = client.player?.inventory ?: return
        if (stacks.size != inventory.containerSize) reset(inventory.containerSize)
        val deadline = System.nanoTime() + BUDGET_NANOS
        for (i in stacks.indices) {
            val stack = inventory.getItem(i)
            if (stack === stacks[i] && stack.count == counts[i]) continue
            if (sightings.batch.full || System.nanoTime() > deadline) break
            if (remember(stacks, counts, i, stack) || stack.isEmpty) continue
            look(stack)
        }
    }

    fun reset(size: Int = 0) {
        if (size == 0 && stacks.isEmpty()) return
        stacks = arrayOfNulls(size)
        counts = IntArray(size)
    }

    private fun look(stack: ItemStack) {
        val entry = stack.itemId ?: return
        if (!sightings.batch.fresh(Look(entry, stack.item, stack.hasFoil(), stack.skinId, stack.skullTexture(), stack.get(DataComponents.LORE)))) return
        sightings.see(stack.toItem())
    }

    private companion object {
        const val BUDGET_NANOS = 1_000_000L
    }
}
