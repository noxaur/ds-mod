package io.github.noxaur.dsmod.core.hud

import kotlin.math.abs

/** Where a dragged HUD block comes to rest when it is let go near something to line up with. */
object HudSnap {
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
