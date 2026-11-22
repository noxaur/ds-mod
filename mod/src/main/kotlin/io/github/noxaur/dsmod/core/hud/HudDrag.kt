package io.github.noxaur.dsmod.core.hud

import kotlin.math.abs

data class HudBox(val id: String, val x: Float, val y: Float, val width: Float, val height: Float)

data class HudDrop(val x: Float, val y: Float, val dock: HudDock = HudDock.FREE)

class HudDrag(block: HudBox, mouseX: Float, mouseY: Float) {
    val id = block.id
    private var grabX = mouseX - block.x
    private var grabY = mouseY - block.y

    fun to(
        block: HudBox,
        mouseX: Float,
        mouseY: Float,
        others: List<HudBox>,
        width: Float,
        height: Float,
        free: Boolean,
        docks: Map<HudDock, HudBox> = emptyMap(),
    ): HudDrop {
        var x = mouseX - grabX
        var y = mouseY - grabY
        if (!free) {
            for ((dock, spot) in docks) if (abs(spot.x - x) <= DOCK_REACH && abs(spot.y - y) <= DOCK_REACH) return HudDrop(spot.x, spot.y, dock)
            val near = others.filter { it.id != id }
            x = HudSnap.snap(
                x, block.width,
                near.flatMap { listOf(it.x, it.x + it.width) } + MARGIN + (width - MARGIN),
                near.map { it.x + it.width / 2 } + width / 2,
                REACH,
            )
            y = HudSnap.snap(
                y, block.height,
                near.flatMap { listOf(it.y, it.y + it.height) } + MARGIN + (height - MARGIN),
                near.map { it.y + it.height / 2 } + height / 2,
                REACH,
            )
        }
        return HudDrop(onScreen(x, block.width, width), onScreen(y, block.height, height))
    }

    fun nudge(dx: Float, dy: Float) {
        grabX -= dx
        grabY -= dy
    }

    companion object {
        private const val REACH = 5f

        private const val DOCK_REACH = 8f

        private const val MARGIN = 2f

        const val NUDGE = 1f
        const val NUDGE_FAR = 10f

        fun nudged(block: HudBox, dx: Float, dy: Float, width: Float, height: Float) = HudDrop(
            onScreen(block.x + dx, block.width, width),
            onScreen(block.y + dy, block.height, height),
        )

        private fun onScreen(at: Float, size: Float, screen: Float) = at.coerceIn(0f, maxOf(0f, screen - size))
    }
}
