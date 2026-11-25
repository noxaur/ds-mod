package io.github.noxaur.dragen.core.container

import io.github.noxaur.dragen.core.text.Role
import io.github.noxaur.dragen.core.text.Text

data class SlotMark(
    val role: Role,
    val label: Text? = null,
    val rarity: Boolean = false,
    val cooldown: SlotCooldown? = null,
    val outline: Boolean = false,
    val count: Int? = null,
    val dim: Boolean = false,
) {
    companion object {
        fun cooldown(start: Long, end: Long) = SlotMark(Role.MUTED, cooldown = SlotCooldown(start, end))
    }
}

data class SlotCooldown(val start: Long, val end: Long)
