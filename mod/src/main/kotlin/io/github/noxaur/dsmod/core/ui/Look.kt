package io.github.noxaur.dsmod.core.ui

import io.github.noxaur.dsmod.core.container.SlotStyle

/**
 * What a value of a pick looks like, which the edge draws: in the pick's dropdown, and on each tile of its menu.
 * A new look is one more case here and one more branch in the edge's `Looks`, and the kit gallery draws every one.
 */
sealed interface Look {
    /** [count] slots marked in [style], from the rarest rarity down, as a container would show them. */
    data class Slots(val style: SlotStyle, val count: Int = 3) : Look

    /** A small panel in [theme]'s colors, holding a well. */
    data class Swatch(val theme: Theme) : Look
}
