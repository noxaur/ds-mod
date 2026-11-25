package io.github.noxaur.dragen.core.hud

import io.github.noxaur.dragen.core.game.Item
import io.github.noxaur.dragen.core.text.Role
import io.github.noxaur.dragen.core.text.Text
import io.github.noxaur.dragen.core.text.TextBuilder
import io.github.noxaur.dragen.core.text.text

class HudGroup(
    val id: String,
    val x: Int,
    val y: Int,
    val background: Boolean = false,
    val scale: Float = 1f,
    val dock: HudDock = HudDock.FREE,
)

data class HudBlock(
    val header: Text,
    val rows: List<HudRow>,
    val look: HudLook = HudLook.PLATE,
    val dock: HudDock = HudDock.FREE,
    val icon: Item? = null,
)

enum class HudLook(private val label: String) {
    PLATE("Plate"),

    CLEAR("Clear"),

    HOTBAR("Hotbar"),
    ;

    override fun toString() = label
}

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

data class BarRow(override val text: Text, val fraction: Float, val role: Role) : HudRow

data class ItemsRow(val items: List<Item?>) : HudRow {
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

    fun line(content: TextBuilder.() -> Unit) {
        rows += TextRow(text(content))
    }

    fun items(items: List<Item?>) {
        rows += ItemsRow(items)
    }
}

fun hud(header: String, build: HudBuilder.() -> Unit) = HudBlock(text { brand(header) }, HudBuilder().apply(build).rows)
