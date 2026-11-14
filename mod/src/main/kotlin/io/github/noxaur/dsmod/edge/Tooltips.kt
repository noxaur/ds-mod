package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.text.Text
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.world.item.ItemStack

/** Lines added to the bottom of an item's hover text. */
object Tooltips {
    private val providers = ArrayList<(ItemStack) -> List<Text>>()

    /** [lines] runs for every hovered item on every frame, so read the item and return, with no lookups that block. */
    fun add(lines: (ItemStack) -> List<Text>) {
        providers += lines
    }

    fun register() {
        ItemTooltipCallback.EVENT.register { stack, _, _, tooltip ->
            for (i in providers.indices) {
                providers[i](stack).forEach { tooltip += it.toComponent() }
            }
        }
    }
}
