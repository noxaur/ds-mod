package io.github.noxaur.dragen.core.hud

import kotlin.math.abs

object HudSnap {
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
        for (line in centers) offer(line - size / 2)
        for (line in edges) offer(line - size)
        for (line in edges) offer(line)
        return best
    }
}
