package io.github.noxaur.dsmod.core.container

import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.Text

/** @keep A label sits where Minecraft draws a stack's count, in place of the fill. `count` is a number there in the mark's
 * role, above 1 only, and keeps the fill. `dim` washes over the item in the well's color, so a server's own green no
 * longer reads as a mark */
data class SlotMark(
    val role: Role,
    val label: Text? = null,
    val rarity: Boolean = false,
    val hide: Boolean = false,
    val cooldown: SlotCooldown? = null,
    val outline: Boolean = false,
    val count: Int? = null,
    val dim: Boolean = false,
) {
    companion object {
        val HIDE = SlotMark(Role.MUTED, hide = true)

        fun cooldown(start: Long, end: Long) = SlotMark(Role.MUTED, cooldown = SlotCooldown(start, end))
    }
}

data class SlotCooldown(val start: Long, val end: Long)
