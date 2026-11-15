package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.hud.HudBlock
import kotlin.reflect.KMutableProperty0

/** How the armor icons line up. */
enum class ArmorLayout(private val label: String) {
    ROW("Row"),
    COLUMN("Column"),
    ;

    override fun toString() = label
}

/**
 * Shows the armor the player wears, helmet first. It reads the player's own inventory, whose slots 5 to 8 are the
 * armor, and keeps the last it saw while another menu is open, since that menu does not hold the armor.
 */
class ArmorDisplay(toggle: KMutableProperty0<Boolean>, private val layout: KMutableProperty0<ArmorLayout>) :
    Feature("armor-display", toggle, Area.ANYWHERE) {
    private var worn = List<Item?>(ARMOR.count()) { null }
    private var shownLayout = layout.get()
    private var block: HudBlock? = null

    init {
        // Neither handler needs to act, because hud reads the shared container. Handling them is what has the edge read the slots.
        on<ContainerOpened> { _, _ -> }
        on<SlotChanged> { _, _ -> }
    }

    override fun hud(now: Long): HudBlock? {
        val next = if (container.id == INVENTORY && container.slots.size > ARMOR.last) ARMOR.map { container.slots[it] } else worn
        if (next != worn || shownLayout != layout.get()) {
            worn = next
            shownLayout = layout.get()
            block = build()
        }
        return block
    }

    private fun build(): HudBlock? = if (worn.all { it == null }) {
        null
    } else {
        block(headed = false) {
            if (shownLayout == ArmorLayout.ROW) items(worn) else worn.forEach { items(listOf(it)) }
        }
    }

    private companion object {
        /** The player's own inventory, which the client always numbers 0. */
        const val INVENTORY = 0

        /** The helmet, chestplate, leggings and boots slots of the player's inventory. */
        val ARMOR = 5..8
    }
}
