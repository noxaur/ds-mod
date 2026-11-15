package io.github.noxaur.dsmod.core.hud

/** A HUD block as it is drawn, in GUI pixels. */
data class HudBox(val id: String, val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * A HUD block the mouse holds, in the HUD editor or on the chat screen. It moves from where it was grabbed, lines
 * up with the screen's edges and center and with the other blocks when it comes near one, and stays on the screen.
 */
class HudDrag(block: HudBox, mouseX: Float, mouseY: Float) {
    val id = block.id
    private val grabX = mouseX - block.x
    private val grabY = mouseY - block.y

    /**
     * Where [block], as it is drawn now, goes with the mouse at [mouseX], [mouseY] on a screen [width] by [height].
     * [free] skips the lining up, as Shift does.
     */
    fun to(block: HudBox, mouseX: Float, mouseY: Float, others: List<HudBox>, width: Float, height: Float, free: Boolean): HudBox {
        var x = mouseX - grabX
        var y = mouseY - grabY
        if (!free) {
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
        return block.copy(x = x.coerceIn(0f, maxOf(0f, width - block.width)), y = y.coerceIn(0f, maxOf(0f, height - block.height)))
    }

    private companion object {
        /** How near a line pulls a block onto it, in GUI pixels. */
        const val REACH = 5f

        /** How far in from the screen's edge a block lines up. */
        const val MARGIN = 2f
    }
}
