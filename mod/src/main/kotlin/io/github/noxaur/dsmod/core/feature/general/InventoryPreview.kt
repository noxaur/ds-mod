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

/**
 * A row the grid can show: its first slot in the inventory menu and how many slots it has. The three rows of the
 * inventory belong together, and the hotbar, the armor and the offhand each stand apart from the rows beside them.
 */
enum class InventoryRow(private val label: String, val first: Int, val width: Int, val main: Boolean) {
    TOP("Top Row", 9, 9, true),
    MIDDLE("Middle Row", 18, 9, true),
    BOTTOM("Bottom Row", 27, 9, true),
    HOTBAR("Hotbar", 36, 9, false),
    ARMOR("Armor", 5, 4, false),
    OFFHAND("Offhand", 45, 1, false),
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

/** How each row lies: across, as the inventory holds it, or down, so the grid stands tall beside the screen's edge. */
enum class InventoryLayout(private val label: String) {
    ROWS("Rows"),
    COLUMNS("Columns"),
    ;

    override fun toString() = label
}

/**
 * Shows the [rows] of the player's inventory they picked, in their order, as a grid of icons with no header, nine to a
 * row as the inventory holds them. An empty cell stands for an empty slot, and the hotbar, armor and offhand sit apart
 * from the rows beside them, as the hotbar does in the inventory. Each row carries its slots' rarity marks, and the
 * hotbar the slot the player holds. The grid hides while a menu is open, which shows the inventory itself, and while
 * every slot picked is empty.
 */
class InventoryPreview(
    toggle: KMutableProperty0<Boolean>,
    private val rows: KMutableProperty0<MutableList<InventoryRow>>,
    private val look: KMutableProperty0<InventoryLook>,
    private val placement: KMutableProperty0<HudDock>,
    private val layout: KMutableProperty0<InventoryLayout>,
    private val opacity: KMutableProperty0<Float>,
    private val pickup: KMutableProperty0<Boolean>,
) : Feature("inventory-preview", toggle, Area.ANYWHERE) {
    /** The settings the block was built with, so a refresh rebuilds it only when one changed. */
    private data class Shown(
        val rows: List<InventoryRow>,
        val look: InventoryLook,
        val placement: HudDock,
        val layout: InventoryLayout,
        val opacity: Float,
        val pickup: Boolean,
    )

    // The inventory menu's slots, from the helmet's to the offhand's, and the rows the block shows of them.
    private var items = emptyList<Item?>()
    private var slots = emptyList<List<Item?>>()
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
        val read = stale && container.id == INVENTORY && container.slots.size > LAST
        if (read) {
            stale = false
            items = container.slots.subList(FIRST, LAST + 1).toList()
        }
        val changed = shown != settings()
        if (changed) shown = settings()
        // A slot the grid does not show, such as armor the player put on, leaves the block as it was.
        if (read || changed) {
            val next = picked().map { row -> items.subList(row.first - FIRST, row.first - FIRST + row.width) }
            if (changed || next != slots) {
                slots = next
                block = build()
            }
        }
        return block
    }

    private fun picked() = shown.rows.distinct()

    private fun settings() = Shown(rows.get().toList(), look.get(), placement.get(), layout.get(), opacity.get(), pickup.get())

    private fun build(): HudBlock? {
        if (items.isEmpty()) return null
        val picked = picked()
        if (slots.all { row -> row.all { it == null } }) return null
        return block(
            headed = false,
            look = shown.look.hud,
            dock = shown.placement,
            overMenus = false,
            columns = shown.layout == InventoryLayout.COLUMNS,
            opacity = shown.opacity.coerceIn(0f, 100f) / 100f,
            pops = shown.pickup,
        ) {
            slots.forEachIndexed { i, row ->
                // Rows of the inventory run on together, and any other row stands apart from what is beside it.
                val apart = i > 0 && !(picked[i].main && picked[i - 1].main)
                items(row, apart = apart, slot = picked[i].first)
            }
        }
    }

    private companion object {
        /** The player's own inventory, which the client always numbers 0. */
        const val INVENTORY = 0
        val FIRST = InventoryRow.entries.minOf { it.first }
        val LAST = InventoryRow.entries.maxOf { it.first + it.width - 1 }
    }
}
