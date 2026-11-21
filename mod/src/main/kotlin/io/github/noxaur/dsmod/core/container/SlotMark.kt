package io.github.noxaur.dsmod.core.container

import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.Text

/**
 * A highlight on one slot of the open container, with an optional label in its corner. It fills the slot
 * behind the item, or with [rarity] it is drawn in the player's [SlotStyle] for that surface, for a mark that most
 * slots carry. With [hide] the slot looks empty instead: no item, no hover highlight and no tooltip. A click still goes
 * to the server. With [cooldown] it fills nothing, and the layer drains the game's own cooldown wash over the item and
 * counts down its seconds, on the hotbar as well.
 */
data class SlotMark(
    val role: Role,
    val label: Text? = null,
    val rarity: Boolean = false,
    val hide: Boolean = false,
    val cooldown: SlotCooldown? = null,
    val outline: Boolean = false,
) {
    companion object {
        val HIDE = SlotMark(Role.MUTED, hide = true)

        fun cooldown(start: Long, end: Long) = SlotMark(Role.MUTED, cooldown = SlotCooldown(start, end))
    }
}

/** A wait that began at [start] and ends at [end], in epoch milliseconds, so the layer can draw each frame of it. */
data class SlotCooldown(val start: Long, val end: Long)
