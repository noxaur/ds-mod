package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.menus.keepcursor.KeepCursor
import io.github.noxaur.dsmod.core.ui.CursorMemory

object MenuCursor {
    val memory = CursorMemory()

    private lateinit var keepCursor: KeepCursor

    fun bind(keepCursor: KeepCursor) {
        this.keepCursor = keepCursor
    }

    val keeps: Boolean get() = keepCursor.feature.runs
}
