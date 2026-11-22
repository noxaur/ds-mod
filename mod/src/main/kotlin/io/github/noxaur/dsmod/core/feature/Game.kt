package io.github.noxaur.dsmod.core.feature

import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.contribute.Places
import io.github.noxaur.dsmod.core.feature.general.DragSimOnly
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.ContainerState
import io.github.noxaur.dsmod.core.game.EntityFilter
import io.github.noxaur.dsmod.core.game.EntityTable
import io.github.noxaur.dsmod.core.game.EntityGone
import io.github.noxaur.dsmod.core.game.EntityRenamed
import io.github.noxaur.dsmod.core.game.EntitySeen
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.InfoRows
import io.github.noxaur.dsmod.core.game.Metrics
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.game.SidebarState
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.core.game.TabState
import io.github.noxaur.dsmod.core.game.LeftServer
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.game.view
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.text.Text
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.world.Mark
import kotlinx.serialization.Serializable

/** The features that have told the player they hide a server line. */
@Serializable
data class Hidden(val told: Set<String> = emptySet())

/**
 * Runs the features. The edge calls [post] for each event and [tick] once a tick, then hands each
 * changed [Readout] to the shared layers, so a feature's own code never runs inside the render loop. A
 * readout is rebuilt after an event its feature could see, and otherwise a few times a second for its clocks.
 * A feature locked to an [io.github.noxaur.dsmod.core.game.Area] gets its events and shows its readout only there.
 * The entity events keep a table of what the client holds, and each feature hears and holds only the entities its
 * filters match.
 * While [active] is false every feature is treated as switched off, which is how the edge keeps the mod to dragsim.co.
 */
class Game(private val features: List<Feature>, val metrics: Metrics = Metrics(), private val connected: () -> Boolean = { true }) {
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

        /** Goes up each time [hud], [slots] or [marks] changes, so the edge hands them over only then. */
        var version = 0
            internal set
        internal var stale = true
        /** Whether the feature hears events, which [sync] alone changes. */
        internal var on = false
        internal var refreshedAt = 0L

        /** Whether the player left the server while the feature was off, so it must forget that session when it comes back. */
        internal var missedLeave = false
    }

    val readouts = features.map(::Readout)

    /** Sees every event before the features do. The debug build records captures through it. */
    var tap: ((time: Long, event: GameEvent) -> Unit)? = null

    private val tab = TabState()

    /** The players the tab list shows now. */
    val tabPlayers get() = tab.players

    /** The places the player has been, which decide the contributions they are offered. The edge saves it. */
    val places = Saved(Places.serializer(), Places())
    private var lastTab: TabList? = null

    /** The features that told the player they hide a line, so each tells them once. The edge saves it. */
    val hidden = Saved(Hidden.serializer(), Hidden())

    /** The sidebar, as of the last event the edge posted. */
    val sidebar = SidebarState()
    private var lastSidebar: Sidebar? = null

    /** Whether the tab list and the sidebar put the player in [area], for the edge to ask on the player's behalf. */
    fun isIn(area: Area) = area.holds(tab, sidebar)

    /** What the features said since the edge last delivered and cleared it. */
    val messages = ArrayList<Message>()

    /** The open container, as of the last event the edge posted. */
    val container = ContainerState()

    /** Each change of world and each leave of the server, as [Feature.worldNumber] reads it. */
    private var worlds = 0

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
            it.container = container
            it.messages = messages
            if (it !== dragSimOnly) it.gate = { active }
            it.worlds = { worlds }
        }
    }

    /**
     * Whether anything would read an event of [type]. A source that costs something asks before it builds
     * one. Every feature may read [tab] and [sidebar], and they decide its area, so both are wanted while any
     * feature is on. A container is wanted only while a feature that handles one is on.
     */
    fun wants(type: Class<out GameEvent>) =
        tap != null || active && features.any { it.enabled && (type == TabList::class.java || type == Sidebar::class.java || type in it.handlers) }

    /**
     * Whether a feature would be handed an event of [type] right now, which also takes the player being in the
     * feature's area. A source whose event comes once, such as a block that is already standing, holds it
     * back until this is true, because [post] gives a feature outside its area nothing.
     */
    fun reaches(type: Class<out GameEvent>) = active && features.any { it.enabled && type in it.handlers && it.area.holds(tab, sidebar) }

    /**
     * The filters of the entities a feature reads now, for the edge's tracker: each one's that is on, and every one's
     * while [tap] records, so that a capture can be cut for any of them. The same list until one of them changes, and
     * empty while nothing reads entities.
     */
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

    /** The text a feature put in place of the [io.github.noxaur.dsmod.core.game.FloatingText] just posted, or null to keep the server's. */
    var replaced: Text? = null
        private set

    /** Returns false when a feature hid [event], which the edge honors for a chat line and a floating text. */
    fun post(time: Long, event: GameEvent): Boolean {
        tap?.invoke(time, event)
        sync(time)
        var shown = true
        replaced = null
        val entity = held.apply(event)
        // Any feature may read the shared state, so a change to it makes every readout stale.
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
            // The new world's tab list and sidebar have not arrived, so the old one's area must not outlive the hop. A
            // disconnect changes no world, and a feature switched on after it must not hear the old server's sidebar.
            WorldChanged, LeftServer -> true.also {
                worlds++
                lastTab = null
                lastSidebar = null
                tab.update(emptyList())
                sidebar.update(Sidebar())
                // A new world numbers its entities again.
                held.clear()
                for (feature in readers) feature.heldEntities.clear()
            }
            else -> false
        }
        for (i in features.indices) {
            val feature = features[i]
            if (!readouts[i].on) {
                if (event == LeftServer) readouts[i].missedLeave = true
                continue
            }
            // Outside its area a feature still follows the tab list, the sidebar, the world and the server, which is how it knows it left.
            val reaches = event is TabList || event is Sidebar || event is WorldChanged || event is LeftServer || feature.area.holds(tab, sidebar)
            val heard = if (entity) feature.entityFilters.view(held.before, held.after, event) ?: continue else event
            val handler = if (reaches) feature.handlers[heard.javaClass] else null
            feature.forget(heard)
            if (handler != null) metrics.time(feature.id) { handler(heard, time) }
            when (heard) {
                is EntitySeen -> feature.heldEntities[heard.id] = heard
                is EntityRenamed -> feature.heldEntities[heard.id] = held.after!!
                is EntityGone -> feature.heldEntities.remove(heard.id)
                else -> Unit
            }
            if (handler != null || shared || entity) readouts[i].stale = true
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

    /**
     * Tells the player where to turn [feature] off, the first time it hides a line, because they did not ask for a
     * server line to go missing. A feature the player turned on themselves hides what they asked it to.
     */
    private fun tellHidden(feature: Feature) {
        hidden.value = Hidden(hidden.value.told + feature.id)
        if (!feature.switch.default) return
        val section = SettingsTree.home(feature.switch) ?: return
        messages += Notice(feature.title, text { body("Hid a message from the server. Turn off "); value(feature.title); body(" under ${section.name} in /dsmod to see them.") })
    }

    /**
     * Turns each feature on or off as its switch and [active] say. [post] does this before an event reaches any
     * feature, so a feature switched on between two ticks never hears an event twice, once as it comes and again
     * as what it missed. A feature that is off sees no events. It forgets its world when it goes off, and hears the
     * tab list and the sidebar again when it comes back, because the next ones arrive only when a row changes. A
     * server it left meanwhile comes first, so it forgets that session before it hears the new one.
     */
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
                feature.handlers[event.javaClass]?.let { metrics.time(feature.id) { it(event, time) } }
            }
            // It hears the entities it reads that the client already holds, as it would have when they came.
            if (!on) feature.heldEntities.clear() else if (feature.entityFilters.isNotEmpty()) for (seen in held.values) {
                if (feature.entityFilters.none { it.matches(seen) }) continue
                feature.handlers[EntitySeen::class.java]?.let { metrics.time(feature.id) { it(seen, time) } }
                feature.heldEntities[seen.id] = seen
            }
        }
    }

    fun tick(time: Long) {
        sync(time)
        for (readout in readouts) {
            val feature = readout.feature
            val enabled = readout.on
            if (!readout.stale && enabled && time - readout.refreshedAt in 0 until REFRESH_MILLIS) continue
            readout.stale = !enabled
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
    }
}
