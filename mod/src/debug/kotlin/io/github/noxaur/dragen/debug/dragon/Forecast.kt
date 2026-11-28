package io.github.noxaur.dragen.debug.dragon

import io.github.noxaur.dragen.api.Tick
import io.github.noxaur.dragen.api.Vec
import io.github.noxaur.dragen.api.boss.dragon.Movement
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

enum class Basis { FLYING, HELD, RAMP, RECOVERING }

data class Tuning(val window: Int, val turnKeep: Double, val climbKeep: Double) {
    companion object {
        val TUNED = Tuning(window = 2, turnKeep = 0.95, climbKeep = 1.0)
    }
}

/** @keep In `protector-dragon` the dragon resumes at the very tick its attack ends, and its first 25 speeds were within 0.001 of each other after the first two attacks, so the ramp is a table. */
object Ramp {
    val SPEEDS = doubleArrayOf(
        0.042, 0.108, 0.184, 0.264, 0.345, 0.42, 0.493, 0.559, 0.62, 0.675, 0.724, 0.769, 0.808, 0.845, 0.877, 0.905, 0.93, 0.953, 0.973, 0.99, 1.006, 1.019, 1.032, 1.041, 1.052,
    )
    const val CRUISE = 1.07

    fun at(tick: Int) = if (tick < 0) 0.0 else SPEEDS.getOrElse(tick) { CRUISE }
}

private const val MOVING = 0.3
private const val NEVER = Int.MAX_VALUE / 2

private fun wrap(angle: Double): Double {
    var a = angle
    while (a > Math.PI) a -= 2 * Math.PI
    while (a < -Math.PI) a += 2 * Math.PI
    return a
}

/** @keep The API keeps the dragon's last three positions and turns at the rate of the last two steps, which one noisy step bends, and it cannot know that a hover ends on the tick its attack does. This keeps a longer path, averages the turn over `window` steps, lets the turn and the climb fade, and follows the hover and the ramp out of it. */
class Forecast(private val tuning: Tuning = Tuning.TUNED) {
    private val path = ArrayDeque<Vec>()
    private var cruiseHeading = 0.0

    val last: Vec? get() = path.lastOrNull()

    fun observe(position: Vec) {
        val before = path.lastOrNull()
        path.addLast(position)
        if (path.size > KEPT) path.removeFirst()
        if (before != null && hypot(position.x - before.x, position.z - before.z) > MOVING) cruiseHeading = atan2(position.z - before.z, position.x - before.x)
    }

    private fun step(from: Int) = path[from + 1] - path[from]

    private fun heading(from: Int) = step(from).let { atan2(it.z, it.x) }

    private fun flat(from: Int) = step(from).let { hypot(it.x, it.z) }

    private fun turn(): Double {
        val steps = path.size - 1
        if (steps < tuning.window + 1) return 0.0
        val first = steps - tuning.window - 1
        if ((first until steps).any { flat(it) < MOVING }) return 0.0
        return wrap(heading(steps - 1) - heading(first)) / tuning.window
    }

    /** @keep Index 0 is the dragon where it is, and index n is the tick n ahead. `endsIn` is the whole ticks until a hover's attack ends, null when the end is unknown. */
    fun positions(movement: Movement, endsIn: Int?, since: Int, count: Int): List<Vec> {
        val here = path.lastOrNull() ?: return emptyList()
        val out = ArrayList<Vec>(count + 1)
        out += here
        val steps = path.size - 1
        val held = movement is Movement.Hovering
        val ramped = movement is Movement.Recovering
        val holds = if (held) endsIn?.coerceAtLeast(0) ?: NEVER else 0
        var heading = if (steps >= 1 && flat(steps - 1) > MOVING && !held) heading(steps - 1) else cruiseHeading
        val turn = if (held || ramped && steps < 1) 0.0 else turn()
        val cruise = if (steps >= 2) (flat(steps - 1) + flat(steps - 2)) / 2 else 0.0
        val climb = if (steps >= 1 && !held) path[steps].y - path[steps - 1].y else 0.0
        var x = here.x
        var y = here.y
        var z = here.z
        for (n in 1..count) {
            val speed = when {
                held -> Ramp.at(n - holds)
                ramped -> Ramp.at(since + n)
                else -> cruise
            }
            heading += turn * tuning.turnKeep.pow(n)
            x += speed * cos(heading)
            z += speed * sin(heading)
            y += climb * tuning.climbKeep.pow(n)
            out += Vec(x, y, z)
        }
        return out
    }

    companion object {
        private const val KEPT = 12

        fun basis(movement: Movement, endsIn: Int?, ahead: Int): Basis = when (movement) {
            Movement.Flying -> Basis.FLYING
            is Movement.Recovering -> Basis.RECOVERING
            is Movement.Diving -> Basis.FLYING
            is Movement.Hovering -> if (endsIn != null && ahead < endsIn) Basis.HELD else Basis.RAMP
        }

        fun endsIn(now: Tick, ends: Tick?): Int? = ends?.let { (it - now).count.toInt() }
    }
}
