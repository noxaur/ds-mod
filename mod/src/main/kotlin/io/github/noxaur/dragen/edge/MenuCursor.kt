package io.github.noxaur.dragen.edge

import io.github.noxaur.dragen.core.feature.menus.keepcursor.KeepCursor
import io.github.noxaur.dragen.core.ui.CursorMemory

object MenuCursor {
    val memory = CursorMemory()

    private lateinit var keepCursor: KeepCursor

    fun bind(keepCursor: KeepCursor) {
        this.keepCursor = keepCursor
    }

    val keeps: Boolean get() = keepCursor.feature.runs
}
