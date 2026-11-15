package io.github.noxaur.dsmod.core.hud

import kotlin.math.abs

/** A HUD block as it is drawn, in GUI pixels. */
data class HudBox(val id: String, val x: Float, val y: Float, val width: Float, val height: Float)

/** Where a dragged block comes to rest: its corner in GUI pixels, or beside the hotbar when [dock] is not [HudDock.FREE]. */
data class HudDrop(val x: Float, val y: Float, val dock: HudDock = HudDock.FREE)

/**
 * A HUD block the mouse holds, in the HUD editor or on the chat screen. It moves from where it was grabbed, lines
 * up with the screen's edges and center and with the other blocks when it comes near one, docks beside the hotbar
 * when it comes near a spot it can dock in, and stays on the screen.
 */
class HudDrag(block: HudBox, mouseX: Float, mouseY: Float) {
    val id = block.id
    private var grabX = mouseX - block.x
    private var grabY = mouseY - block.y

    /**
     * Where [block], as it is drawn now, goes with the mouse at [mouseX], [mouseY] on a screen [width] by [height].
     * [docks] are the boxes it would fill beside the hotbar, for a block that can dock. [free] skips the lining up
     * and the docking, as Shift does.
     */
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
        return HudDrop(x.coerceIn(0f, maxOf(0f, width - block.width)), y.coerceIn(0f, maxOf(0f, height - block.height)))
    }

    /** Moves the block [dx], [dy] off the mouse, so it stays that far from where the mouse would put it. */
    fun nudge(dx: Float, dy: Float) {
        grabX -= dx
        grabY -= dy
    }

    companion object {
        /** How near a line pulls a block onto it, in GUI pixels. */
        private const val REACH = 5f

        /** How near a dock spot pulls a block into it, on both axes. Wider than a line, since it takes a corner. */
        private const val DOCK_REACH = 8f

        /** How far in from the screen's edge a block lines up. */
        private const val MARGIN = 2f

        /** How far an arrow key nudges a block, in GUI pixels, and with Shift. */
        const val NUDGE = 1f
        const val NUDGE_FAR = 10f

        /** Where an arrow nudges [block] by [dx], [dy], kept on a screen [width] by [height]. */
        fun nudged(block: HudBox, dx: Float, dy: Float, width: Float, height: Float) = HudDrop(
            (block.x + dx).coerceIn(0f, maxOf(0f, width - block.width)),
            (block.y + dy).coerceIn(0f, maxOf(0f, height - block.height)),
        )
    }
}
