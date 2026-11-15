package io.github.noxaur.dsmod.core.container

import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.Text

/**
 * A highlight on one slot of the open container, with an optional label in its corner. It fills the slot
 * behind the item, or with [rarity] it is drawn in the player's [SlotStyle] for that surface, for a mark that most
 * slots carry.
 */
data class SlotMark(val role: Role, val label: Text? = null, val rarity: Boolean = false)
