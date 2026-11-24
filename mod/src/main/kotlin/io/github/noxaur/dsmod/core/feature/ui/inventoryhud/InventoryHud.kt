package io.github.noxaur.dsmod.core.feature.ui.inventoryhud

import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.KeyBind
import io.github.noxaur.dsmod.core.game.ContainerState.Companion.CARRIED_SLOTS
import io.github.noxaur.dsmod.core.game.ContainerState.Companion.OFFHAND_SLOT
import io.github.noxaur.dsmod.core.game.ContainerState.Companion.ROW_SLOTS
import io.github.noxaur.dsmod.core.game.ContainerState.Companion.WORN_SLOTS
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.hud.HudDock
import io.github.noxaur.dsmod.core.hud.HudLook
import io.github.noxaur.dsmod.core.feature.ContainerReader

enum class InventoryRow(private val label: String, val slots: IntRange, val main: Boolean) {
    TOP("Top Row", carriedRow(0), true),
    MIDDLE("Middle Row", carriedRow(1), true),
    BOTTOM("Bottom Row", carriedRow(2), true),
    HOTBAR("Hotbar", carriedRow(3), false),
    ARMOR("Armor", WORN_SLOTS, false),
    OFFHAND("Offhand", OFFHAND_SLOT..OFFHAND_SLOT, false),
    ;

    override fun toString() = label
}

private fun carriedRow(row: Int) = (CARRIED_SLOTS.first + row * ROW_SLOTS).let { it until it + ROW_SLOTS }

enum class InventoryLook(private val label: String, val hud: HudLook) {
    PLATE("Plate", HudLook.PLATE),
    CLEAR("Clear", HudLook.CLEAR),
    INVENTORY("Inventory", HudLook.INVENTORY),
    ;

    override fun toString() = label
}

enum class InventoryLayout(private val label: String) {
    ROWS("Rows"),
    COLUMNS("Columns"),
    ;

    override fun toString() = label
}

class InventoryHud(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "inventory-hud", Area.ANYWHERE, Store.GENERAL, "Show a grid of the items in your inventory on the HUD.", default = false, formerly = listOf("inventoryPreview"), hud = ::hud)
    private val inventory = ContainerReader(feature, FIRST..LAST)

    val shownRows = feature.order("inventoryRows", "Rows", "Pick the rows to show, and drag them into order.", listOf(InventoryRow.TOP, InventoryRow.MIDDLE, InventoryRow.BOTTOM))
    val look = feature.choice("inventoryLook", "Look", "Show the items on the plate, on nothing, or as your inventory.", InventoryLook.INVENTORY)
    val layout = feature.choice("inventoryLayout", "Layout", "Lay each row out across, as in the inventory, or down.", InventoryLayout.ROWS)
    val placement = feature.placement("inventoryPlacement", "Keep the grid where you put it, or beside your hotbar.")
    val opacity = feature.number("inventoryOpacity", "Opacity", "Set how solid the Inventory look's panel is.", 100f, 0f..100f, step = 5f, unit = "%")

    val key = feature.keys("inventoryKey", "Key", "Pick a key that shows or hides the grid, or none to always show it.", KeyBind.NONE)
    val keyMode = feature.choice("inventoryKeyMode", "Key Mode", "Pick Hold to show the grid while the key is down, or Toggle.", KeyMode.TOGGLE)
    val pickup = feature.switch("inventoryPickup", "Pickup Animation", "Bob an item in the grid as it lands, as the hotbar does.", default = false)

    private data class Shown(
        val rows: List<InventoryRow>,
        val look: InventoryLook,
        val placement: HudDock,
        val layout: InventoryLayout,
        val opacity: Float,
        val pickup: Boolean,
    )

    private var items = emptyList<Item?>()
    private var slots = emptyList<List<Item?>>()
    private var shown = settings()
    private var block: HudBlock? = null

    fun hud(now: Long): HudBlock? {
        val read = inventory.changed()
        if (read) {
            items = feature.container.slots.subList(FIRST, LAST + 1).toList()
        }
        val changed = shown != settings()
        if (changed) shown = settings()
        if (read || changed) {
            val next = picked().map { row -> items.subList(row.slots.first - FIRST, row.slots.last - FIRST + 1) }
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
        return feature.block(
            headed = false,
            look = shown.look.hud,
            dock = shown.placement,
            overMenus = false,
            columns = shown.layout == InventoryLayout.COLUMNS,
            opacity = shown.opacity.coerceIn(0f, 100f) / 100f,
            pops = shown.pickup,
        ) {
            slots.forEachIndexed { i, row ->
                val apart = i > 0 && !(picked[i].main && picked[i - 1].main)
                items(row, apart = apart, slot = picked[i].slots.first)
            }
        }
    }

    private companion object {
        val FIRST = InventoryRow.entries.minOf { it.slots.first }
        val LAST = InventoryRow.entries.maxOf { it.slots.last }
    }
}
