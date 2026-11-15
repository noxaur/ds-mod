package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.hud.HudDock
import io.github.noxaur.dsmod.core.hud.HudLook
import kotlin.reflect.KMutableProperty0

/** A row of the player's inventory and its first slot in the inventory menu. */
enum class InventoryRow(private val label: String, val first: Int) {
    TOP("Top Row", 9),
    MIDDLE("Middle Row", 18),
    BOTTOM("Bottom Row", 27),
    HOTBAR("Hotbar", 36),
    ;

    override fun toString() = label
}

/** How the grid shows: on the HUD's plate, on nothing, or as the player's inventory screen draws it. */
enum class InventoryLook(private val label: String, val hud: HudLook) {
    PLATE("Plate", HudLook.PLATE),
    CLEAR("Clear", HudLook.CLEAR),
    INVENTORY("Inventory", HudLook.INVENTORY),
    ;

    override fun toString() = label
}

/**
 * Shows the [rows] of the player's inventory they picked, in their order, as a grid of icons with no header, nine to a
 * row as the inventory holds them. An empty cell stands for an empty slot, and the hotbar sits apart from the rows
 * beside it, as it does in the inventory. Each row carries its slots' rarity marks, and the hotbar the slot the player
 * holds. The grid hides while a menu is open, which shows the inventory itself, and while every slot picked is empty.
 */
class InventoryPreview(
    toggle: KMutableProperty0<Boolean>,
    private val rows: KMutableProperty0<MutableList<InventoryRow>>,
    private val look: KMutableProperty0<InventoryLook>,
    private val placement: KMutableProperty0<HudDock>,
) : Feature("inventory-preview", toggle, Area.ANYWHERE) {
    /** The settings the block was built with, so a refresh rebuilds it only when one changed. */
    private data class Shown(val rows: List<InventoryRow>, val look: InventoryLook, val placement: HudDock)

    // The inventory's slots from the top row's first to the hotbar's last.
    private var items = emptyList<Item?>()
    private var shown = settings()
    private var block: HudBlock? = null

    // Whether the inventory may have changed since it was last read. A refresh with nothing new then costs a few compares.
    private var stale = true

    init {
        on<ContainerOpened> { event, _ -> if (event.id == INVENTORY) stale = true }
        on<SlotChanged> { event, _ -> if (container.id == INVENTORY && event.slot in FIRST..LAST) stale = true }
        // Also run when the feature is switched off, so it reads the inventory again when it comes back.
        on<WorldChanged> { _, _ -> stale = true }
    }

    override fun hud(now: Long): HudBlock? {
        if (stale && container.id == INVENTORY && container.slots.size > LAST) {
            stale = false
            val next = container.slots.subList(FIRST, LAST + 1)
            if (next != items) {
                items = next.toList()
                block = build()
            }
        }
        if (shown.rows != rows.get() || shown.look != look.get() || shown.placement != placement.get()) {
            shown = settings()
            block = build()
        }
        return block
    }

    private fun settings() = Shown(rows.get().toList(), look.get(), placement.get())

    private fun build(): HudBlock? {
        if (items.isEmpty()) return null
        val picked = shown.rows.distinct()
        val slots = picked.map { row -> items.subList(row.first - FIRST, row.first - FIRST + WIDTH) }
        if (slots.all { row -> row.all { it == null } }) return null
        return block(headed = false, look = shown.look.hud, dock = shown.placement, overMenus = false) {
            slots.forEachIndexed { i, row ->
                items(row, apart = i > 0 && (picked[i] == InventoryRow.HOTBAR) != (picked[i - 1] == InventoryRow.HOTBAR), slot = picked[i].first)
            }
        }
    }

    private companion object {
        /** The player's own inventory, which the client always numbers 0. */
        const val INVENTORY = 0
        const val WIDTH = 9
        val FIRST = InventoryRow.TOP.first
        val LAST = InventoryRow.HOTBAR.first + WIDTH - 1
    }
}
