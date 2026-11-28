package io.github.noxaur.dragen.debug.dragon

import io.github.noxaur.dragen.api.Box
import io.github.noxaur.dragen.api.Vec
import kotlin.math.abs
import kotlin.math.pow

/** @keep In `2026-11-27T16-19-31` every arrow of the server's Precise Golden Gun left at 3.0 blocks a tick, 2.9718 on average and 3.0006 at most, and then lost 1% of its speed and 0.05 blocks a tick of height each tick, exactly as vanilla's arrow does. */
object Arrow {
    const val SPEED = 3.0
    const val DRAG = 0.99
    const val GRAVITY = 0.05

    /** @keep Vanilla's hit test grows an entity's box by 0.3 on every side before it tests the arrow's centre. */
    const val GROW = 0.3

    private const val TERMINAL = GRAVITY / (1 - DRAG)

    private fun reach(ticks: Double) = (1 - DRAG.pow(ticks)) / (1 - DRAG)

    /** @keep Summing `position += velocity; velocity = velocity * DRAG - GRAVITY` over a whole number of ticks gives this closed form, and it stays smooth for a fraction of a tick. */
    fun position(from: Vec, velocity: Vec, ticks: Double): Vec {
        val along = reach(ticks)
        return Vec(from.x + velocity.x * along, from.y + (velocity.y + TERMINAL) * along - TERMINAL * ticks, from.z + velocity.z * along)
    }

    fun velocityFor(from: Vec, to: Vec, ticks: Double): Vec {
        val along = reach(ticks)
        return Vec((to.x - from.x) / along, (to.y - from.y + TERMINAL * ticks) / along - TERMINAL, (to.z - from.z) / along)
    }

    fun reaches(box: Box, point: Vec): Boolean {
        val across = box.width / 2 + GROW
        val up = box.height / 2 + GROW
        return abs(point.x - box.center.x) <= across && abs(point.z - box.center.z) <= across && abs(point.y - box.center.y) <= up
    }
}

/** @keep `reachable` is false when no flight time lets a 3.0 arrow arrive, and then `miss` is how far short the best flight falls. */
data class Shot(val direction: Vec, val flight: Double, val miss: Double, val reachable: Boolean, val arrival: Vec) {
    fun aimPoint(eye: Vec): Vec = eye + direction * (arrival - eye).length
}

object Aim {
    private const val FIRST = 1.0
    private const val LAST = 60.0
    private const val STEP = 0.5
    private const val REFINE = 24

    /** @keep The target is asked for a flight time counted in arrow ticks from the launch, and the arrow is taken at the first flight time that a 3.0 arrow can meet, since a later one is a lob that gives the dragon longer to turn. */
    fun solve(eye: Vec, target: (Double) -> Vec): Shot {
        var best = Double.MAX_VALUE
        var bestAt = FIRST
        var before = FIRST
        var at = FIRST
        while (at <= LAST) {
            val speed = Arrow.velocityFor(eye, target(at), at).length
            if (speed < best) {
                best = speed
                bestAt = at
            }
            if (speed <= Arrow.SPEED) return shot(eye, target, refine(eye, target, before.coerceAtMost(at), at), true)
            before = at
            at += STEP
        }
        return shot(eye, target, bestAt, false)
    }

    private fun refine(eye: Vec, target: (Double) -> Vec, lo: Double, hi: Double): Double {
        var low = lo
        var high = hi
        repeat(REFINE) {
            val mid = (low + high) / 2
            if (Arrow.velocityFor(eye, target(mid), mid).length <= Arrow.SPEED) high = mid else low = mid
        }
        return high
    }

    private fun shot(eye: Vec, target: (Double) -> Vec, flight: Double, reachable: Boolean): Shot {
        val arrival = target(flight)
        val needed = Arrow.velocityFor(eye, arrival, flight)
        val miss = if (reachable) 0.0 else (needed.length - Arrow.SPEED) * (1 - Arrow.DRAG.pow(flight)) / (1 - Arrow.DRAG)
        return Shot(needed * (1 / needed.length), flight, miss, reachable, arrival)
    }
}
