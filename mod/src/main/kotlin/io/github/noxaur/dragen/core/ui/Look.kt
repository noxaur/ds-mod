package io.github.noxaur.dragen.core.ui

import io.github.noxaur.dragen.core.container.SlotStyle

sealed interface Look {
    data class Slots(val style: SlotStyle, val count: Int = 3) : Look

    data class Swatch(val theme: Theme) : Look
}
