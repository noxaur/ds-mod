package io.github.noxaur.dsmod.edge.storage

import io.github.noxaur.dsmod.core.overview.Look
import io.github.noxaur.dsmod.core.overview.Owned
import io.github.noxaur.dsmod.core.overview.StoragePages
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.edge.Slots
import io.github.noxaur.dsmod.edge.profile.toStack
import io.github.noxaur.dsmod.edge.toStack
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import java.util.IdentityHashMap

/** @keep The stacks a remembered page draws, rebuilt one slot at a time when its item changes, so a frame builds no
 * stack. A page is kept by identity, and a new session's pages are new ones. */
object PageStacks {
    class Stacks(size: Int) {
        internal val from = arrayOfNulls<Owned>(size)
        val stacks = arrayOfNulls<ItemStack>(size)
        val rarities = arrayOfNulls<Role>(size)
    }

    private val cache = IdentityHashMap<StoragePages.Page, Stacks>()

    fun of(page: StoragePages.Page): Stacks {
        val made = cache.getOrPut(page) { Stacks(page.items.size) }
        val items = page.items
        for (i in items.indices) {
            val item = items[i]
            if (item === made.from[i]) continue
            made.from[i] = item
            made.stacks[i] = item?.let(::stack)
            made.rarities[i] = item?.rarity?.let { name -> Role.RARITIES.firstOrNull { it.name == name } }
        }
        return made
    }

    /** @keep Forgets the pages no longer kept, called when the pages change rather than per frame */
    fun keep(pages: Collection<StoragePages.Page>) {
        if (cache.size > pages.size) cache.keys.retainAll(pages.toSet())
    }

    /** @keep One remembered item in its cell, with its rarity mark, as a menu's slot draws it. `x` and `y` are the item's. */
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
