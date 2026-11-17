package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.KeyBind
import io.github.noxaur.dsmod.core.game.ContainerState.Companion.INVENTORY
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.hud.HudDock
import io.github.noxaur.dsmod.core.hud.HudLook

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
 * Shows the [shownRows] of the player's inventory they picked, in their order, as a grid of icons with no header, nine to a
 * row as the inventory holds them. An empty cell stands for an empty slot, and the hotbar, armor and offhand sit apart
 * from the rows beside them, as the hotbar does in the inventory. Each row carries its slots' rarity marks, and the
 * hotbar the slot the player holds. The grid hides while a menu is open, which shows the inventory itself, and while
 * every slot picked is empty.
 */
class InventoryHud(config: DsModConfig) :
    InventoryReader(config, "inventory-hud", "Show a grid of the items in your inventory on the HUD.", default = false, formerly = listOf("inventoryPreview")) {
    val shownRows = order("inventoryRows", "Rows", "Pick the rows to show, and drag them into order.", listOf(InventoryRow.TOP, InventoryRow.MIDDLE, InventoryRow.BOTTOM))
    val look = choice("inventoryLook", "Look", "Show the items on the plate, on nothing, or as your inventory.", InventoryLook.INVENTORY)
    val layout = choice("inventoryLayout", "Layout", "Lay each row out across, as in the inventory, or down.", InventoryLayout.ROWS)
    override val placement = choice("inventoryPlacement", "Placement", "Keep the grid where you put it, or beside your hotbar.", HudDock.FREE)
    val opacity = number("inventoryOpacity", "Opacity", "Set how solid the Inventory look's panel is.", 100f, 0f..100f, step = 5f, unit = "%")

    /** The key that shows or hides the grid, and how, which `InventoryKey` in the edge reads. None always shows it. */
    val key = keys("inventoryKey", "Key", "Pick a key that shows or hides the grid, or none to always show it.", KeyBind.NONE)
    val keyMode = choice("inventoryKeyMode", "Key Mode", "Pick Hold to show the grid while the key is down, or Toggle.", KeyMode.TOGGLE)
    val pickup = switch("inventoryPickup", "Pickup Animation", "Bob an item in the grid as it lands, as the hotbar does.", default = false)

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

    init {
        on<SlotChanged> { event, _ -> if (container.id == INVENTORY && event.slot in FIRST..LAST) stale = true }
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

    private fun settings() = Shown(shownRows.value, look.value, placement.value, layout.value, opacity.value, pickup.value)

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
        val FIRST = InventoryRow.entries.minOf { it.first }
        val LAST = InventoryRow.entries.maxOf { it.first + it.width - 1 }
    }
}
