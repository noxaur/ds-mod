package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.hud.HudBlock
import kotlin.reflect.KMutableProperty0

/** A piece of armor, and the slot of the player's inventory that holds it. */
enum class ArmorPiece(private val label: String, val slot: Int) {
    HELMET("Helmet", 5),
    CHESTPLATE("Chestplate", 6),
    LEGGINGS("Leggings", 7),
    BOOTS("Boots", 8),
    ;

    override fun toString() = label
}

/** How the armor icons line up. */
enum class ArmorLayout(private val label: String) {
    ROW("Row"),
    COLUMN("Column"),
    ;

    override fun toString() = label
}

/**
 * Shows the [pieces] of armor the player picked, in their order, with an empty cell for one not worn. It reads the
 * player's own inventory, and keeps the last it saw while another menu is open, since that menu does not hold the armor.
 */
class ArmorDisplay(
    toggle: KMutableProperty0<Boolean>,
    private val layout: KMutableProperty0<ArmorLayout>,
    private val pieces: KMutableProperty0<MutableList<ArmorPiece>>,
) : Feature("armor-display", toggle, Area.ANYWHERE) {
    private var worn = emptyMap<ArmorPiece, Item>()
    private var shownLayout = layout.get()
    private var shownPieces = pieces.get().toList()
    private var block: HudBlock? = null

    // Whether the armor may have changed since it was last read. A refresh with nothing new then costs two compares.
    private var stale = true

    init {
        on<ContainerOpened> { event, _ -> if (event.id == INVENTORY) stale = true }
        on<SlotChanged> { event, _ -> if (container.id == INVENTORY && ArmorPiece.entries.any { it.slot == event.slot }) stale = true }
        // Also run when the feature is switched off, so it reads the armor again when it comes back.
        on<WorldChanged> { _, _ -> stale = true }
    }

    override fun hud(now: Long): HudBlock? {
        if (stale && container.id == INVENTORY && container.slots.size > LAST_SLOT) {
            stale = false
            val next = read()
            if (next != worn) {
                worn = next
                block = build()
            }
        }
        if (shownLayout != layout.get() || shownPieces != pieces.get()) {
            shownLayout = layout.get()
            shownPieces = pieces.get().toList()
            block = build()
        }
        return block
    }

    private fun read(): Map<ArmorPiece, Item> {
        val map = HashMap<ArmorPiece, Item>()
        for (piece in ArmorPiece.entries) container.slots[piece.slot]?.let { map[piece] = it }
        return map
    }

    private fun build(): HudBlock? {
        val shown = shownPieces.distinct().map { worn[it] }
        if (shown.all { it == null }) return null
        return block(headed = false) {
            if (shownLayout == ArmorLayout.ROW) items(shown) else shown.forEach { items(listOf(it)) }
        }
    }

    private companion object {
        /** The player's own inventory, which the client always numbers 0. */
        const val INVENTORY = 0

        /** The inventory's slots up to the boots. */
        val LAST_SLOT = ArmorPiece.entries.maxOf { it.slot }
    }
}
