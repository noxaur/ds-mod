package io.github.noxaur.dragen.debug.dragon

import io.github.noxaur.dragen.api.Box
import io.github.noxaur.dragen.api.Known
import io.github.noxaur.dragen.api.Tick
import io.github.noxaur.dragen.api.Vec
import io.github.noxaur.dragen.api.boss.dragon.Dragon
import io.github.noxaur.dragen.api.boss.dragon.Movement
import kotlin.math.hypot
import kotlin.math.roundToInt

/** @keep `confidence` is null while the error table has no cell for the basis, and `miss` is the shot's own shortfall joined with the median error of that cell. */
data class Aimed(val part: String, val shot: Shot, val aimPoint: Vec, val at: Box, val basis: Basis, val ahead: Int, val confidence: Double?, val miss: Double, val samples: Int)

/** @keep The server shows the arrow two flight ticks after the click, one client tick later, so a flight of n ticks arrives n - 1 client ticks from now. */
class AimTracker {
    private val forecasts = HashMap<Long, Forecast>()
    private var seen = Long.MIN_VALUE
    private var solved: Map<Long, List<Aimed>> = emptyMap()

    val solutions: Map<Long, List<Aimed>> get() = solved

    fun observe(dragons: List<Dragon>, now: Tick, eye: Vec?) {
        if (now.whole == seen) return
        seen = now.whole
        forecasts.keys.retainAll(dragons.map { it.id.value }.toSet())
        solved = dragons.associate { dragon ->
            val forecast = forecasts.getOrPut(dragon.id.value) { Forecast() }
            forecast.observe(dragon.body.position)
            dragon.id.value to if (eye == null) emptyList() else aim(dragon, forecast, Tick.of(now.whole), eye)
        }
    }

    private fun aim(dragon: Dragon, forecast: Forecast, now: Tick, eye: Vec): List<Aimed> {
        val movement = dragon.movement
        val ends = (dragon.attack?.ends as? Known.Value)?.value
        val endsIn = Forecast.endsIn(now, ends)
        val since = (movement as? Movement.Recovering)?.let { (now - it.since).count.toInt() } ?: 0
        val path = forecast.positions(movement, endsIn, since, HORIZON)
        if (path.isEmpty()) return emptyList()
        val here = path.first()
        return listOf("body", "head").mapNotNull { name ->
            val part = dragon.body.parts.firstOrNull { it.name == name } ?: return@mapNotNull null
            val shot = Aim.solve(eye) { flight ->
                val ahead = (flight - 1).coerceIn(0.0, HORIZON - 1.0)
                val whole = ahead.toInt()
                val f = ahead - whole
                val moved = path[whole] + (path[whole + 1] - path[whole]) * f - here
                part.box.center + moved
            }
            val ahead = (shot.flight - 1).roundToInt().coerceAtLeast(0)
            val basis = Forecast.basis(movement, endsIn, ahead)
            val cell = ErrorTable.at(basis, ahead)
            val confidence = cell?.let { if (name == "head") it.head else it.body }
            Aimed(name, shot, shot.aimPoint(eye), Box(shot.arrival, part.box.width, part.box.height), basis, ahead, confidence, hypot(shot.miss, cell?.median ?: 0.0), cell?.samples ?: 0)
        }
    }

    private companion object {
        const val HORIZON = 61
    }
}
