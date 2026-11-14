package io.github.noxaur.dsmod.core.feature

import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.ContainerState
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.Metrics
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.core.game.TabState
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.hud.HudBlock

/**
 * Runs the features. The edge calls [post] for each event and [tick] once a tick, then hands each
 * changed [Readout] to the shared layers, so a feature's own code never runs inside the render loop. A
 * readout is rebuilt after an event its feature could see, and otherwise a few times a second for its clocks.
 */
class Game(private val features: List<Feature>, val metrics: Metrics = Metrics()) {
    class Readout(val feature: Feature) {
        internal val metric = "${feature.id}.hud"
        internal val slotsMetric = "${feature.id}.slots"
        var hud: HudBlock? = null
            internal set
        var slots: Map<Int, SlotMark> = emptyMap()
            internal set

        /** Goes up each time [hud] or [slots] changes, so the edge hands them over only then. */
        var version = 0
            internal set
        internal var stale = true
        internal var on = feature.enabled
        internal var refreshedAt = 0L
    }

    val readouts = features.map(::Readout)

    /** Sees every event before the features do. The debug build records captures through it. */
    var tap: ((time: Long, event: GameEvent) -> Unit)? = null

    private val tab = TabState()

    /** The open container, as of the last event the edge posted. */
    val container = ContainerState()

    init {
        features.forEach {
            it.metrics = metrics
            it.tab = tab
            it.container = container
        }
    }

    /**
     * Whether anything would read an event of [type]. A source that costs something asks before it builds
     * one. Every feature may read [tab], so a tab list is wanted while any feature is on. A container is
     * wanted only while a feature that handles one is on.
     */
    fun wants(type: Class<out GameEvent>) =
        tap != null || features.any { it.enabled && (type == TabList::class.java || type in it.handlers) }

    fun post(time: Long, event: GameEvent) {
        tap?.invoke(time, event)
        // Any feature may read the shared state, so a change to it makes every readout stale.
        val shared = when (event) {
            is TabList -> true.also { metrics.time("tab-list") { tab.update(event.rows) } }
            is ContainerOpened -> true.also { metrics.time("container") { container.opened(event) } }
            is SlotChanged -> true.also { metrics.time("container") { container.changed(event) } }
            else -> false
        }
        for (i in features.indices) {
            val feature = features[i]
            if (!feature.enabled) continue
            val handler = feature.handlers[event.javaClass]
            if (handler != null) metrics.time(feature.id) { handler(event, time) }
            if (handler != null || shared) readouts[i].stale = true
        }
    }

    fun tick(time: Long) {
        for (readout in readouts) {
            val feature = readout.feature
            if (readout.on != feature.enabled) {
                readout.on = feature.enabled
                // A feature that is off sees no events, so what it knows of this world would be stale when it comes back.
                if (!readout.on) feature.handlers[WorldChanged::class.java]?.let { metrics.time(feature.id) { it(WorldChanged, time) } }
            }
            if (!readout.stale && feature.enabled && time - readout.refreshedAt in 0 until REFRESH_MILLIS) continue
            readout.stale = !feature.enabled
            readout.refreshedAt = time
            var hud: HudBlock? = null
            var slots = emptyMap<Int, SlotMark>()
            if (feature.enabled) {
                metrics.time(readout.metric) { hud = feature.hud(time) }
                metrics.time(readout.slotsMetric) { slots = feature.slots(time) }
            }
            if (hud == readout.hud && slots == readout.slots) continue
            readout.hud = hud
            readout.slots = slots
            readout.version++
        }
    }

    private companion object {
        const val REFRESH_MILLIS = 250L
    }
}
