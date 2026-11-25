package io.github.noxaur.dragen.core.feature

import io.github.noxaur.dragen.core.game.LeftServer
import io.github.noxaur.dragen.core.game.ServerTime
import io.github.noxaur.dragen.core.game.WorldChanged

class ServerClock(feature: Feature) {
    private val arrived = ArrayDeque<Long>()
    private val ticks = ArrayDeque<Long>()
    private var offset = 0L
    private var shift = 0L

    var updates = 0
        private set

    init {
        feature.on<ServerTime> { time, now -> record(now, time.gameTime) }
        feature.on<WorldChanged> { _, now -> clear(now) }
        feature.on<LeftServer> { _, now -> clear(now) }
    }

    private fun record(now: Long, gameTime: Long) {
        if (ticks.isNotEmpty() && gameTime < ticks.last()) clear(now)
        if (ticks.isEmpty()) offset = now + shift - gameTime * TICK_MILLIS
        arrived.addLast(now)
        ticks.addLast(gameTime)
        while (arrived.size > 2 && now - arrived[1] >= TPS_WINDOW_MILLIS) {
            arrived.removeFirst()
            ticks.removeFirst()
        }
        updates++
    }

    private fun clear(now: Long) {
        shift = millis(now) - now
        arrived.clear()
        ticks.clear()
        updates++
    }

    fun millis(now: Long): Long {
        if (ticks.isEmpty()) return now + shift
        val since = (now - arrived.last()).coerceIn(0, STALL_MILLIS)
        return offset + ticks.last() * TICK_MILLIS + (since * rate() / TICKS_PER_SECOND).toLong()
    }

    fun wall(span: Long): Long = (span * TICKS_PER_SECOND / rate().coerceAtLeast(MIN_RATE)).toLong()

    private fun rate(): Double {
        val last = ticks.size - 1
        if (last < 1) return TICKS_PER_SECOND
        val span = arrived[last] - arrived[last - 1]
        if (span <= 0) return TICKS_PER_SECOND
        val rate = (ticks[last] - ticks[last - 1]) * 1000.0 / span
        return if (rate >= HEALTHY_RATE) TICKS_PER_SECOND else rate.coerceAtLeast(0.0)
    }

    fun tps(now: Long): Double? {
        if (arrived.isEmpty()) return null
        val newest = arrived.last()
        val end = if (now - newest > STALL_MILLIS) now else newest
        val span = end - arrived.first()
        if (span < MIN_SPAN_MILLIS) return null
        return ((ticks.last() - ticks.first()) * 1000.0 / span).coerceIn(0.0, TICKS_PER_SECOND)
    }

    private companion object {
        const val TICK_MILLIS = 50L
        const val TICKS_PER_SECOND = 20.0
        const val TPS_WINDOW_MILLIS = 10_000L
        const val MIN_SPAN_MILLIS = 1_500L
        const val STALL_MILLIS = 3_000L
        const val HEALTHY_RATE = 19.5
        const val MIN_RATE = 1.0
    }
}
