package io.github.noxaur.dsmod.core.hud

import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.Text
import io.github.noxaur.dsmod.core.text.TextBuilder
import io.github.noxaur.dsmod.core.text.text

/**
 * A place on the HUD, in GUI pixels. Blocks shown in the same group stack under each other in the
 * order they were first shown, and share the group's background and [scale]. A group with a [dock] other than
 * [HudDock.FREE] ignores [x] and [y] and sits beside the hotbar.
 */
class HudGroup(
    val id: String,
    val x: Int,
    val y: Int,
    val background: Boolean = false,
    val scale: Float = 1f,
    val dock: HudDock = HudDock.FREE,
)

/**
 * One block on the HUD: a header and the rows under it. A block whose header is empty shows only its rows. [look]
 * and [dock] are the player's, for a feature that lets them choose. A block not [overMenus] hides while a menu is open,
 * for one that would repeat what the menu shows. With [columns] each row of icons stands as a column, left to right.
 * [opacity], from 0 to 1, is how solid the Inventory look's panel and slots are. A block that [pops] bobs an icon of
 * the player's inventory as an item lands in its slot, as the hotbar does.
 */
data class HudBlock(
    val header: Text,
    val rows: List<HudRow>,
    val look: HudLook = HudLook.PLATE,
    val dock: HudDock = HudDock.FREE,
    val overMenus: Boolean = true,
    val columns: Boolean = false,
    val opacity: Float = 1f,
    val pops: Boolean = false,
)

/** What a block sits on. */
enum class HudLook(private val label: String) {
    /** The HUD's plate, when the player has the HUD Background on. */
    PLATE("Plate"),

    /** Nothing, so the block floats over the world. */
    CLEAR("Clear"),

    /** No plate, and its icons in slots drawn from the hotbar's own texture, so a resource pack restyles both. */
    HOTBAR("Hotbar"),

    /**
     * A panel with its icons in 18px slots, as the player's inventory screen draws them: by the kit in the player's theme,
     * solid or as glass as their Menu Look says, while Menu Theme is on, and from the inventory's own texture while it is off.
     */
    INVENTORY("Inventory"),
    ;

    override fun toString() = label
}

/** Where a block sits: where the player put it, or flush beside the hotbar, past the offhand slot and the attack indicator. */
enum class HudDock(private val label: String) {
    FREE("Free"),
    LEFT_OF_HOTBAR("Left of Hotbar"),
    RIGHT_OF_HOTBAR("Right of Hotbar"),
    ;

    override fun toString() = label
}

sealed interface HudRow {
    val text: Text
}

data class TextRow(override val text: Text) : HudRow

/** A row with a bar under it, filled from 0 to 1. */
data class BarRow(override val text: Text, val fraction: Float, val role: Role) : HudRow

/**
 * A row of item icons, left to right, with an empty cell for each null. A row [apart] from the one above it leaves the
 * gap the inventory leaves above the hotbar. A row that shows the player's own inventory from its [slot] on, numbered as
 * the inventory menu numbers them, carries what the inventory's slots carry: the marks of Item Rarity, and on the
 * hotbar the slot the player holds. [text] names them, for the replay's transcript.
 */
data class ItemsRow(val items: List<Item?>, val apart: Boolean = false, val slot: Int = -1) : HudRow {
    override val text: Text get() = text { muted(items.joinToString(", ") { it?.name?.plain ?: it?.id ?: "-" }) }
}

class HudBuilder {
    val rows = mutableListOf<HudRow>()

    fun row(label: String, content: TextBuilder.() -> Unit) {
        rows += TextRow(labeled(label, content))
    }

    fun bar(label: String, fraction: Float, role: Role = Role.VALUE, content: TextBuilder.() -> Unit = {}) {
        rows += BarRow(labeled(label, content), fraction.coerceIn(0f, 1f), role)
    }

    private fun labeled(label: String, content: TextBuilder.() -> Unit) = text { muted("$label: "); content() }

    /** A row with no label, for a line that names itself, such as a pet as the server writes it. */
    fun line(content: TextBuilder.() -> Unit) {
        rows += TextRow(text(content))
    }

    fun items(items: List<Item?>, apart: Boolean = false, slot: Int = -1) {
        rows += ItemsRow(items, apart, slot)
    }
}

fun hud(header: String, build: HudBuilder.() -> Unit) = HudBlock(text { brand(header) }, HudBuilder().apply(build).rows)
