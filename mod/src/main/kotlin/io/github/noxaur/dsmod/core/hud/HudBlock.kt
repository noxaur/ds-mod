package io.github.noxaur.dsmod.core.hud

import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.Text
import io.github.noxaur.dsmod.core.text.TextBuilder
import io.github.noxaur.dsmod.core.text.text

/**
 * A place on the HUD, in GUI pixels. Blocks shown in the same group stack under each other in the
 * order they were first shown, and share the group's background.
 */
class HudGroup(val id: String, val x: Int, val y: Int, val background: Boolean = false)

/** One block on the HUD: a header and the rows under it. */
class HudBlock(val header: Text, val rows: List<HudRow>)

sealed interface HudRow {
    val text: Text
}

class TextRow(override val text: Text) : HudRow

/** A row with a bar under it, filled from 0 to 1. */
class BarRow(override val text: Text, val fraction: Float, val role: Role) : HudRow

class HudBuilder {
    val rows = mutableListOf<HudRow>()

    fun row(label: String, content: TextBuilder.() -> Unit) {
        rows += TextRow(text { muted("$label: "); content() })
    }

    fun bar(label: String, fraction: Float, role: Role = Role.VALUE, content: TextBuilder.() -> Unit = {}) {
        rows += BarRow(text { muted("$label: "); content() }, fraction.coerceIn(0f, 1f), role)
    }
}

fun hud(header: String, build: HudBuilder.() -> Unit) = HudBlock(text { brand(header) }, HudBuilder().apply(build).rows)
