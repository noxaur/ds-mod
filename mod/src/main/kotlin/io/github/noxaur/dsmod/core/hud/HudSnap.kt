package io.github.noxaur.dsmod.core.hud

import kotlin.math.abs

/** A HUD block's box on the screen, in GUI pixels. */
class HudBox(val x: Float, val y: Float, val width: Float, val height: Float)

/** Where a dragged HUD block comes to rest when it is let go near something to line up with. */
object HudSnap {
    /** How near, in GUI pixels, a block's edge or middle comes to a line before it takes it. */
    const val REACH = 5f

    /** How far in from the screen's edge a block lines up. */
    const val MARGIN = 2f

    /**
     * Where a block [width] by [height], dragged to [x], [y] on a screen [screenWidth] by [screenHeight], comes to
     * rest: lined up with the screen's edges and middle and with the [others], unless [free] says the player holds
     * Shift, then kept whole on the screen, so it can never be lost past an edge.
     */
    fun place(x: Float, y: Float, width: Float, height: Float, others: List<HudBox>, screenWidth: Float, screenHeight: Float, free: Boolean): Pair<Float, Float> {
        var px = x
        var py = y
        if (!free) {
            px = snap(x, width, others.flatMap { listOf(it.x, it.x + it.width) } + MARGIN + (screenWidth - MARGIN), others.map { it.x + it.width / 2 } + screenWidth / 2, REACH)
            py = snap(y, height, others.flatMap { listOf(it.y, it.y + it.height) } + MARGIN + (screenHeight - MARGIN), others.map { it.y + it.height / 2 } + screenHeight / 2, REACH)
        }
        return px.coerceIn(0f, (screenWidth - width).coerceAtLeast(0f)) to py.coerceIn(0f, (screenHeight - height).coerceAtLeast(0f))
    }

    /**
     * Where a block [size] long, dragged to [at] on one axis, comes to rest. Its start or its end takes the
     * nearest of [edges] within [reach], or its middle the nearest of [centers], and it stays at [at] when
     * nothing is that near.
     */
    fun snap(at: Float, size: Float, edges: List<Float>, centers: List<Float>, reach: Float): Float {
        var best = at
        var distance = reach
        fun offer(candidate: Float) {
            val away = abs(candidate - at)
            if (away <= distance) {
                distance = away
                best = candidate
            }
        }
        // Offered last to first, so a start wins a tie with an end, and an edge with a center.
        for (line in centers) offer(line - size / 2)
        for (line in edges) offer(line - size)
        for (line in edges) offer(line)
        return best
    }
}
