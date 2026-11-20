package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.FrameRate
import io.github.noxaur.dsmod.core.game.PingMeasured
import io.github.noxaur.dsmod.core.game.ServerTime
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.hud.HudLook
import io.github.noxaur.dsmod.core.text.TextBuilder
import io.github.noxaur.dsmod.core.text.grouped
import java.util.Locale

/** A value Performance Stats can show. */
enum class Stat(private val label: String) {
    FPS("FPS"),
    PING("Ping"),
    TPS("TPS"),
    ;

    override fun toString() = label
}

/** How Performance Stats lines its values up. */
enum class StatsLayout(private val label: String) {
    ROW("Row"),
    COLUMN("Column"),
    ;

    override fun toString() = label
}

/** What Performance Stats sits on. */
enum class StatsLook(private val label: String, val hud: HudLook) {
    PLATE("Plate", HudLook.PLATE),
    CLEAR("Clear", HudLook.CLEAR),
    ;

    override fun toString() = label
}

/**
 * Shows the client's frames per second, the round trip to the server and the server's ticks per second. dragsim.co
 * writes 0 for every player's ping in the tab list, so `Pings` in the edge asks the server once a second, with the
 * request the F3 network chart sends, and only while Ping is shown. Ping is the mean of the last few answers, so one
 * slow answer moves it without making it jump.
 *
 * The server sends its world's tick count about once a second. The ticks between the oldest and the newest of the last
 * ten seconds, over the time between them, are its rate, which a few milliseconds of network jitter moves by less than
 * a tenth of a tick. A server that stops ticking stops sending, so once the newest is a few seconds old the wait counts
 * as time with no ticks, and the rate falls for as long as the server stands still. Each world has its own clock, so a
 * change of world starts the window again.
 */
class PerformanceStats(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "performance-stats", Area.ANYWHERE, Store.GENERAL, "Show your FPS, your ping and the server's TPS on the HUD.", default = false, hud = ::hud)

    val values = feature.order("performanceValues", "Values", "Pick the values to show, and drag them into order.", Stat.entries)
    val layout = feature.choice("performanceLayout", "Layout", "Line the values up in a row or a column.", StatsLayout.ROW)
    val look = feature.choice("performanceLook", "Look", "Show the values on the plate, or on nothing.", StatsLook.PLATE)

    private var fps = -1
    private val pings = LongArray(PINGS)
    private var pingCount = 0

    // When each time packet arrived, and the tick it carried, oldest first.
    private val arrived = ArrayDeque<Long>()
    private val ticks = ArrayDeque<Long>()

    init {
        feature.on<FrameRate> { frames, _ -> fps = frames.fps }
        feature.on<PingMeasured> { ping, _ ->
            pings[pingCount % PINGS] = ping.millis
            pingCount++
        }
        feature.on<ServerTime> { time, now ->
            // A clock that went back is another world's, as after a hop the client saw no change of world for.
            if (ticks.isNotEmpty() && time.gameTime < ticks.last()) clearTicks()
            arrived.addLast(now)
            ticks.addLast(time.gameTime)
            while (arrived.size > 2 && now - arrived[1] >= TPS_WINDOW_MILLIS) {
                arrived.removeFirst()
                ticks.removeFirst()
            }
        }
        feature.on<WorldChanged> { _, _ -> clearTicks() }
    }

    private fun clearTicks() {
        arrived.clear()
        ticks.clear()
    }

    fun hud(now: Long): HudBlock? {
        val shown = values.value
        if (shown.isEmpty()) return null
        val ping = if (pingCount == 0) null else (0 until minOf(pingCount, PINGS)).sumOf { pings[it] } / minOf(pingCount, PINGS)
        val tps = tps(now)
        fun TextBuilder.show(stat: Stat) = when (stat) {
            Stat.FPS -> if (fps < 0) muted("-") else value(fps.toLong())
            Stat.PING -> if (ping == null) muted("-") else {
                val millis = grouped(ping)
                when {
                    ping > PING_BAD -> bad(millis)
                    ping > PING_WARNING -> warning(millis)
                    else -> value(millis)
                }
                muted(" ms")
            }
            Stat.TPS -> if (tps == null) muted("-") else {
                val rate = "%.1f".format(Locale.US, tps)
                when {
                    tps < TPS_BAD -> bad(rate)
                    tps < TPS_WARNING -> warning(rate)
                    else -> value(rate)
                }
            }
        }
        return feature.block(headed = false, look = look.value.hud) {
            if (layout.value == StatsLayout.COLUMN) {
                for (stat in shown) row(stat.toString()) { show(stat) }
            } else {
                line {
                    shown.forEachIndexed { i, stat ->
                        muted(if (i == 0) "$stat: " else "   $stat: ")
                        show(stat)
                    }
                }
            }
        }
    }

    /** The server's ticks per second, or null until the window spans long enough to tell. */
    private fun tps(now: Long): Double? {
        if (arrived.isEmpty()) return null
        val newest = arrived.last()
        val end = if (now - newest > STALL_MILLIS) now else newest
        val span = end - arrived.first()
        if (span < MIN_SPAN_MILLIS) return null
        return ((ticks.last() - ticks.first()) * 1000.0 / span).coerceIn(0.0, MAX_TPS)
    }

    private companion object {
        const val PINGS = 5
        const val PING_WARNING = 150L
        const val PING_BAD = 300L
        const val TPS_WINDOW_MILLIS = 10_000L

        // Three of the server's once-a-second clocks, two seconds apart give or take the network's jitter.
        const val MIN_SPAN_MILLIS = 1_500L

        // The server sends its clock once a second, so three seconds without it is a server that stopped.
        const val STALL_MILLIS = 3_000L
        const val MAX_TPS = 20.0
        const val TPS_WARNING = 18.0
        const val TPS_BAD = 12.0
    }
}
