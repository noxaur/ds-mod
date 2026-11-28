package io.github.noxaur.dragen.core.feature

import io.github.noxaur.dragen.api.ServerClock
import io.github.noxaur.dragen.core.config.SettingsTree
import io.github.noxaur.dragen.core.container.SlotMark
import io.github.noxaur.dragen.core.contribute.Places
import io.github.noxaur.dragen.core.feature.general.dragen.dragsimonly.DragSimOnly
import io.github.noxaur.dragen.core.game.Area
import io.github.noxaur.dragen.core.game.ContainerOpened
import io.github.noxaur.dragen.core.game.ContainerState
import io.github.noxaur.dragen.core.game.EntityFilter
import io.github.noxaur.dragen.core.game.EntityTable
import io.github.noxaur.dragen.core.game.EntityGone
import io.github.noxaur.dragen.core.game.EntityRenamed
import io.github.noxaur.dragen.core.game.EntitySeen
import io.github.noxaur.dragen.core.game.GameEvent
import io.github.noxaur.dragen.core.game.InfoRows
import io.github.noxaur.dragen.core.game.Metrics
import io.github.noxaur.dragen.core.game.Sidebar
import io.github.noxaur.dragen.core.game.SidebarState
import io.github.noxaur.dragen.core.game.SlotChanged
import io.github.noxaur.dragen.core.game.ServerTime
import io.github.noxaur.dragen.core.game.TabList
import io.github.noxaur.dragen.core.game.TabState
import io.github.noxaur.dragen.core.game.LeftServer
import io.github.noxaur.dragen.core.game.WorldChanged
import io.github.noxaur.dragen.core.game.view
import io.github.noxaur.dragen.core.hud.HudBlock
import io.github.noxaur.dragen.core.player.PlayerData
import io.github.noxaur.dragen.core.text.Text
import io.github.noxaur.dragen.core.text.text
import io.github.noxaur.dragen.core.world.Mark
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Serializable
data class Hidden(val told: Set<String> = emptySet())

class Game(
    private val features: List<Feature>,
    val metrics: Metrics = Metrics(),
    private val worker: Executor = BACKGROUND,
    private val connected: () -> Boolean = { true },
) {
    private val dragSimOnly = features.firstOrNull { it.id == DragSimOnly.ID }

    val active: Boolean get() = connected() || dragSimOnly?.enabled != true

    class Readout(val feature: Feature) {
        internal val metric = "${feature.id}.hud"
        internal val slotsMetric = "${feature.id}.slots"
        internal val marksMetric = "${feature.id}.marks"
        var hud: HudBlock? = null
            internal set
        var slots: Map<Int, SlotMark> = emptyMap()
            internal set
        var marks: List<Mark> = emptyList()
            internal set

        var version = 0
            internal set
        internal var stale = true
        internal var on = false
        internal var sharedSeen = 0
        internal var refreshedAt = 0L

        internal var missedLeave = false
    }

    val readouts = features.map(::Readout)

    val clock = ServerClock()

    private val finished = ConcurrentLinkedQueue<() -> Unit>()

    var tap: ((time: Long, event: GameEvent) -> Unit)? = null

    private val tab = TabState()

    val tabPlayers get() = tab.players

    val places = Saved(Places.serializer(), Places())
    private var lastTab: TabList? = null

    val hidden = Saved(Hidden.serializer(), Hidden())

    val sidebar = SidebarState()
    private var lastSidebar: Sidebar? = null

    fun isIn(area: Area) = area.holds(tab, sidebar)

    val messages = ArrayList<Message>()

    val container = ContainerState()

    val player = PlayerData(container, metrics)

    private var worlds = 0

    private var sharedChanges = 0
    private val everyone = IntArray(features.size) { it }
    private val hearing = HashMap<Class<out GameEvent>, IntArray>()

    private val held = EntityTable()
    private val readers = features.filter { it.entityFilters.isNotEmpty() }.also { check(it.size <= Long.SIZE_BITS) }
    private var reading = 0L
    private var filters = emptyList<EntityFilter>()

    init {
        features.forEach {
            it.metrics = metrics
            it.tab = InfoRows(tab::info)
            it.sidebar = InfoRows(sidebar::info)
            it.inArea = { it.area.holds(tab, sidebar) }
            it.named = { Area.named(tab, sidebar) }
            it.container = container
            it.player = player
            it.state?.takeIf { state -> state.profiled }?.let { state -> player.slice(it.id, state) }
            it.messages = messages
            if (it !== dragSimOnly) it.gate = { active }
            it.worlds = { worlds }
            it.worker = { work -> worker.execute(work) }
            it.handBack = { done -> finished.add(done) }
            it.clock = clock
        }
    }

    fun wants(type: Class<out GameEvent>) =
        tap != null || type == ServerTime::class.java || active && (player.wants(type) ||features.any { it.enabled && (type == TabList::class.java || type == Sidebar::class.java || type in it.handlers) })

    fun reaches(type: Class<out GameEvent>) = active && features.any { it.enabled && type in it.handlers && it.area.holds(tab, sidebar) }

    fun entityFilters(): List<EntityFilter> {
        val all = tap != null
        val on = all || active
        var mask = 0L
        for (i in readers.indices) if (all || on && readers[i].enabled) mask = mask or (1L shl i)
        if (mask != reading) {
            reading = mask
            filters = readers.filterIndexed { i, _ -> mask and (1L shl i) != 0L }.flatMap { it.entityFilters }.distinct()
        }
        return filters
    }

    var replaced: Text? = null
        private set

    fun post(time: Long, event: GameEvent): Boolean {
        sync(time)
        return deliver(time, event)
    }

    fun post(time: Long, events: List<GameEvent>) {
        sync(time)
        for (event in events) deliver(time, event)
    }

    private fun deliver(time: Long, event: GameEvent): Boolean {
        tap?.invoke(time, event)
        var shown = true
        replaced = null
        val entity = held.apply(event)
        val shared = when (event) {
            is TabList -> true.also {
                lastTab = event
                metrics.time("tab-list") {
                    tab.update(event.rows)
                    places.value = places.value.with(tab, sidebar)
                }
            }
            is Sidebar -> true.also {
                lastSidebar = event
                metrics.time("sidebar") {
                    sidebar.update(event)
                    places.value = places.value.with(tab, sidebar)
                }
            }
            is ContainerOpened -> true.also { metrics.time("container") { container.opened(event) } }
            is SlotChanged -> true.also { metrics.time("container") { container.changed(event) } }
            is ServerTime -> true.also { clock.time(event.gameTime, time) }
            WorldChanged, LeftServer -> true.also {
                clock.clear(time)
                worlds++
                lastTab = null
                lastSidebar = null
                tab.update(emptyList())
                sidebar.update(Sidebar())
                held.clear()
                for (feature in readers) feature.heldEntities.clear()
            }
            else -> false
        }
        val switched = active && player.fold(time, event)
        if (shared || switched) sharedChanges++
        val all = entity || event == WorldChanged || event == LeftServer
        val listeners = if (all) everyone else hearing.getOrPut(event.javaClass) { features.indices.filter { event.javaClass in features[it].handlers }.toIntArray() }
        for (i in listeners) {
            val feature = features[i]
            if (!readouts[i].on) {
                if (event == LeftServer) readouts[i].missedLeave = true
                continue
            }
            val reaches = event is TabList || event is Sidebar || event is WorldChanged || event is LeftServer || feature.area.holds(tab, sidebar)
            val heard = if (entity) feature.entityFilters.view(held.before, held.after, event) ?: continue else event
            val handler = if (reaches) feature.handlers[heard.javaClass] else null
            feature.forget(heard)
            if (handler != null) metrics.time(feature.id) { handler.handle(heard, time) }
            when (heard) {
                is EntitySeen -> feature.heldEntities[heard.id] = heard
                is EntityRenamed -> feature.heldEntities[heard.id] = checkNotNull(held.after) { "a renamed entity is held after the rename" }
                is EntityGone -> feature.heldEntities.remove(heard.id)
                else -> Unit
            }
            if (handler != null || entity) readouts[i].stale = true
            feature.replacement?.let {
                feature.replacement = null
                replaced = it
            }
            if (feature.hides) {
                feature.hides = false
                shown = false
                if (feature.id !in hidden.value.told) tellHidden(feature)
            }
        }
        return shown
    }

    private fun tellHidden(feature: Feature) {
        hidden.value = Hidden(hidden.value.told + feature.id)
        if (!feature.switch.default) return
        val section = SettingsTree.home(feature.switch) ?: return
        messages += Notice(feature.title, text { body("Hid a message from the server. Turn off "); value(feature.title); body(" under ${section.name} in /dragen to see them.") })
    }

    /** @keep The server resends the tab list and the sidebar only when a row changes. */
    private fun sync(time: Long) {
        val active = active
        for (readout in readouts) {
            val feature = readout.feature
            val on = (active || feature === dragSimOnly) && feature.enabled
            if (readout.on == on) continue
            readout.on = on
            val missed = if (on) listOfNotNull(LeftServer.takeIf { readout.missedLeave }, lastTab, lastSidebar) else listOf(WorldChanged)
            readout.missedLeave = false
            for (event in missed) {
                feature.forget(event)
                feature.handlers[event.javaClass]?.let { metrics.time(feature.id) { it.handle(event, time) } }
            }
            if (!on) feature.heldEntities.clear() else if (feature.entityFilters.isNotEmpty()) for (seen in held.values) {
                if (feature.entityFilters.none { it.matches(seen) }) continue
                feature.handlers[EntitySeen::class.java]?.let { metrics.time(feature.id) { it.handle(seen, time) } }
                feature.heldEntities[seen.id] = seen
            }
        }
    }

    fun tick(time: Long) {
        while (true) finished.poll()?.invoke() ?: break
        sync(time)
        for (readout in readouts) {
            val feature = readout.feature
            val enabled = readout.on
            if (!readout.stale && readout.sharedSeen == sharedChanges && !feature.redrawn && enabled && time - readout.refreshedAt in 0 until REFRESH_MILLIS) continue
            readout.stale = !enabled
            readout.sharedSeen = sharedChanges
            feature.redrawn = false
            readout.refreshedAt = time
            var hud: HudBlock? = null
            var slots = emptyMap<Int, SlotMark>()
            var marks = emptyList<Mark>()
            if (enabled && feature.area.holds(tab, sidebar)) {
                metrics.time(readout.metric) { hud = feature.hud?.invoke(time) }
                metrics.time(readout.slotsMetric) { slots = feature.slots?.invoke(time) ?: emptyMap() }
                metrics.time(readout.marksMetric) { marks = feature.marks?.invoke(time) ?: emptyList() }
            }
            if (hud == readout.hud && slots == readout.slots && marks == readout.marks) continue
            readout.hud = hud
            readout.slots = slots
            readout.marks = marks
            readout.version++
        }
    }

    internal companion object {
        const val REFRESH_MILLIS = 250L

        private val BACKGROUND: Executor = Executors.newSingleThreadExecutor { Thread(it, "dragen-background").apply { isDaemon = true } }
    }
}
