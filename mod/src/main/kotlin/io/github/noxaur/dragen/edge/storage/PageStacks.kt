package io.github.noxaur.dragen.edge.storage

import io.github.noxaur.dragen.core.game.Item
import io.github.noxaur.dragen.core.overview.Look
import io.github.noxaur.dragen.core.overview.Owned
import io.github.noxaur.dragen.core.player.Seen
import io.github.noxaur.dragen.core.text.Role
import io.github.noxaur.dragen.edge.Slots
import io.github.noxaur.dragen.edge.profile.toStack
import io.github.noxaur.dragen.edge.toStack
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack

object PageStacks {
    class Stacks(size: Int) {
        internal val from = arrayOfNulls<Item>(size)
        val owned = arrayOfNulls<Owned>(size)
        val stacks = arrayOfNulls<ItemStack>(size)
        val rarities = arrayOfNulls<Role>(size)
    }

    private val cache = HashMap<String, Stacks>()

    fun of(id: String, page: Seen): Stacks {
        val items = page.items
        val made = cache[id]?.takeIf { it.from.size == items.size } ?: Stacks(items.size).also { cache[id] = it }
        for (i in items.indices) {
            val item = items[i]
            if (item === made.from[i]) continue
            made.from[i] = item
            val owned = item?.let(Owned::of)
            made.owned[i] = owned
            made.stacks[i] = owned?.let(::stack)
            made.rarities[i] = owned?.rarity?.let { name -> Role.RARITIES.firstOrNull { it.name == name } }
        }
        return made
    }

    fun keep(ids: Set<String>) {
        cache.keys.retainAll(ids)
    }

    fun draw(graphics: GuiGraphicsExtractor, font: Font, stacks: Stacks, i: Int, x: Int, y: Int) {
        val stack = stacks.stacks[i] ?: return
        stacks.rarities[i]?.takeIf { Slots.marks(it) }?.let { Slots.rarityBack(graphics, it, i, x, y) }
        graphics.item(stack, x, y)
        graphics.itemDecorations(font, stack, x, y)
    }

    private fun stack(item: Owned): ItemStack = when (val look = item.look) {
        is Look.Drawn -> look.item.toStack()
        is Look.Seen -> look.item.toStack()
    }
}
