package io.github.noxaur.dsmod.core.feature

import io.github.noxaur.dsmod.core.config.Bind
import io.github.noxaur.dsmod.core.config.Canvas
import io.github.noxaur.dsmod.core.config.Choice
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Fold
import io.github.noxaur.dsmod.core.config.KeyBind
import io.github.noxaur.dsmod.core.config.Number
import io.github.noxaur.dsmod.core.config.Order
import io.github.noxaur.dsmod.core.config.Pick
import io.github.noxaur.dsmod.core.config.Rows
import io.github.noxaur.dsmod.core.config.Setting
import io.github.noxaur.dsmod.core.config.Slot
import io.github.noxaur.dsmod.core.config.Span
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.config.Switch
import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerState
import io.github.noxaur.dsmod.core.game.EntityFilter
import io.github.noxaur.dsmod.core.game.EntitySeen
import io.github.noxaur.dsmod.core.game.FloatingText
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.Metrics
import io.github.noxaur.dsmod.core.game.InfoRows
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.LeftServer
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.hud.HudBuilder
import io.github.noxaur.dsmod.core.hud.HudDock
import io.github.noxaur.dsmod.core.hud.HudLook
import io.github.noxaur.dsmod.core.text.Text
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.Look
import io.github.noxaur.dsmod.core.ui.Section
import io.github.noxaur.dsmod.core.world.Mark
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * One thing the mod does for the player: a class that builds its [Feature] and holds it as [feature], as
 * `override val feature = Feature(config, "dragon-timer", ...)`, declared before anything that uses it. `SettingsTree`
 * places it, and the edge finds it by its class, as `DsModClient.feature<Animations>()`, to read its settings.
 */
interface HasFeature {
    val feature: Feature
}

/**
 * The part a feature holds, which `Game` runs. A feature turns events into its own state in [on], and turns that state
 * into what the player sees in the [hud], [slots] and [marks] it passes here, says something once with [notice] and
 * [alert], or keeps a server line out of chat with [hide]. The shared layers draw and print all of it, so a feature touches no screen itself. [switch] is
 * its switch in the settings, and while that is off, none of these run.
 * Outside [area] the feature shows nothing and sees only the tab list, the sidebar and a change of world.
 *
 * A feature declares itself once, here. Its id gives its title, which heads its HUD block and names its switch, and
 * [description] is what the switch's row says. Its other settings are declared as rows, as `val shortest = feature.number(...)`,
 * each saved under its key in the feature's [store] of the config file, so a feature file is the one place a setting is
 * named, described and given its default. `SettingsTree` places each feature once, which is also what lists it: a
 * feature placed nowhere never runs, and `FeaturesListedTest` fails on it. Two features that share behaviour each hold
 * a part for it, as Boss Beacon and Boss Tracer hold `BossMarks`, never a base class.
 *
 * Start from the feature closest to yours:
 * - Follow lines in chat: `DragonTimer`.
 * - Read the tab list's info rows, or the sidebar's the same way: `ProtectorStatus`.
 * - Read items and mark slots: `ItemRarity`.
 * - Show items on the HUD, from the player's own inventory or an entity near them: `EquipmentDisplay`.
 * - Change how the game draws, reading nothing from the server: `Fullbright`.
 * - Count something and keep the total: `SlayerTracker`.
 * - Hide a line the server sends: `HideCooldownMessages`.
 * - Shorten or hide text the server floats in the world, such as damage numbers: `DamageNumbers`.
 * - Tell the player something once, and read a setting besides the switch: `RareDropAlert`.
 * - Track a rate, and follow the player into and out of its area: `ZealotTracker`.
 * - Mark a place in the world, and read settings of a few kinds: `ProtectorSpawnBeam`.
 * - Read the entities the client holds, and mark them in the world: `BossBeacon`, with its part `BossMarks`.
 * - Act on a key the player binds, or change the camera: `FreeLook` and `Camera`, with `Cameras` in `edge` and the
 *   rules in `core/camera`.
 * - Handle the player's own input on a vanilla screen, reading nothing from the server: `CopyChat`, with `CopyChats`
 *   in `edge`.
 * - Settings the edge reads each frame, and a button that resets them: `Animations`, with `HeldItems` in `edge`.
 */
class Feature(
    config: DsModConfig,
    val id: String,
    val area: Area,
    /** The section of the config file this feature's settings are saved in. */
    val store: Store,
    description: String,
    default: Boolean = true,
    /** The keys older versions saved the switch under, so a player who updates keeps it. */
    formerly: List<String> = emptyList(),
    /**
     * This feature's block on the HUD, built with [block], or null to show nothing. Called after an event
     * this feature could see and a few times a second, never per frame.
     */
    internal val hud: ((now: Long) -> HudBlock?)? = null,
    /**
     * The marks on the open container, by slot index, or none. Called when [hud] is, so return the same
     * map until the marks change. A mark on a slot the open container lacks is not drawn.
     */
    internal val slots: ((now: Long) -> Map<Int, SlotMark>)? = null,
    /** The marks this feature draws in the world, or none. Called when [hud] is, so return equal marks until they change. */
    internal val marks: ((now: Long) -> List<Mark>)? = null,
) {
    val title = id.split('-').joinToString(" ") { WORDS[it] ?: it.replaceFirstChar(Char::uppercase) }
    private val header = text { brand(title) }

    private val stored = config.stored(store)

    /** The rows under the switch, declared in order. `SettingsTree` lists them under it, or beside it as a group. */
    @PublishedApi
    internal val settings = Rows()

    /**
     * The switch in the settings that turns this feature on and off. Its row's id is [id], which a favorite and a link
     * to it use, and it is saved under [id] in camel case, as `protectorSpawnBeam`, the key its field always had.
     */
    val switch: Switch = Switch(id, title, description, slot(camel(id), default, serializer(), formerly).bind)

    /** The feature's own rows in order, which `SettingsTree` lists under its [switch]. */
    val rows: List<Setting> get() = settings.rows

    var enabled: Boolean
        get() = switch.value
        set(value) {
            switch.value = value
        }

    /**
     * Whether the feature is on wherever the player is: its switch is on and the mod is active on this server. Code
     * outside `core` asks this or [runs] and never the switch, because off dragsim.co a switch that is on still runs nothing.
     */
    val on: Boolean get() = enabled && gate()

    /** Whether the feature runs now: it is [on] and the player is in [area]. The edge asks this before it sends or blocks input. */
    val runs: Boolean get() = on && inArea()

    internal var gate: () -> Boolean = { true }

    internal val handlers = HashMap<Class<out GameEvent>, (GameEvent, Long) -> Unit>()
    internal lateinit var metrics: Metrics
    /**
     * The tab list's `Key: value` rows, read with [InfoRows.info]. `Game` keeps the rows that tell the area, so
     * pass [area] instead of reading one. A feature that needs another part of the tab list extends [InfoRows].
     */
    internal lateinit var tab: InfoRows

    /** The sidebar's `Key: value` rows, read and extended as [tab] is. */
    internal lateinit var sidebar: InfoRows
    internal lateinit var inArea: () -> Boolean
    internal lateinit var container: ContainerState
    internal lateinit var messages: MutableList<Message>
    internal var hides = false

    /**
     * The number of the world the player is in, which goes up with each change of world and each time they leave the
     * server, whether or not this feature heard it. A part that two features share tells by it that its world ended.
     */
    val worldNumber: Int get() = worlds()

    internal var worlds: () -> Int = { 0 }

    /** Whether the player is in [area], for a tab-list or sidebar handler that follows them arriving and leaving. */
    val here: Boolean get() = inArea()

    /** What this feature keeps between sessions, or null when it keeps nothing. The edge loads and writes it. */
    var state: Saved<*>? = null
        private set

    /** Declares the state that outlives the session. A feature has at most one. */
    inline fun <reified S : Any> saved(default: S) = saved(serializer<S>(), default)

    @PublishedApi
    internal fun <S : Any> saved(serializer: KSerializer<S>, default: S): Saved<S> {
        check(state == null) { "$id already saves state" }
        return Saved(serializer, default).also { state = it }
    }

    private val inWorld = ArrayList<Scoped<*>>()
    private val inSession = ArrayList<Scoped<*>>()

    /**
     * State that holds for one world, as `private var phase by feature.world { Phase.Unknown }`. `Game` builds it again from
     * [initial] when the world changes, the player leaves the server or the feature goes off, before the feature hears
     * why, so no handler has to remember each value.
     */
    fun <S> world(initial: () -> S) = Scoped(initial).also { inWorld += it }

    /** State that holds for one session on the server, which a change of world keeps, built again as [world] is when the player leaves. */
    fun <S> session(initial: () -> S) = Scoped(initial).also { inSession += it }

    /** Forgets the world or the session that [event] ends. */
    internal fun forget(event: GameEvent) {
        if (event != WorldChanged && event != LeftServer) return
        for (scoped in inWorld) scoped.reset()
        if (event == LeftServer) for (scoped in inSession) scoped.reset()
    }

    /**
     * A value saved under [key] in [store] with no row of its own, for a row that reads and writes several, such as a
     * look built from parts. [formerly] names the keys older versions wrote it under.
     */
    inline fun <reified T> stored(key: String, default: T, vararg formerly: String) = slot(key, default, serializer<T>(), formerly.toList())

    @PublishedApi
    internal fun <T> slot(key: String, default: T, serializer: KSerializer<T>, formerly: List<String>): Slot<T> = stored.slot(key, default, serializer, formerly)

    /** An on-off setting saved under [key]. Rows declared in [settings] open under it. */
    fun switch(key: String, name: String, description: String, default: Boolean, dependsOn: Switch? = null, settings: (Rows.() -> Unit)? = null): Switch =
        this.settings.switch(stored(key, default).bind, name, description, key, dependsOn, settings)

    /** A number from the start of [range] to its end in steps of [step], shown with [unit] after it. */
    fun number(
        key: String, name: String, description: String, default: Float, range: ClosedFloatingPointRange<Float>, step: Float, unit: String = "",
        dependsOn: Switch? = null,
    ): Number = settings.number(stored(key, default).bind, name, description, range, step, unit, key, dependsOn)

    /** One of [E]'s values, shown by name. */
    inline fun <reified E : Enum<E>> choice(key: String, name: String, description: String, default: E, dependsOn: Switch? = null): Choice<E> =
        settings.choice(enumBind(key, default), name, description, enumList<E>(), key, dependsOn)

    /** One of [E]'s values, each shown as [look] draws it. */
    inline fun <reified E : Enum<E>> pick(key: String, name: String, description: String, default: E, noinline look: (E) -> Look): Pick<E> =
        settings.pick(enumBind(key, default), name, description, enumList<E>(), key, look = look)

    @PublishedApi
    internal inline fun <reified E : Enum<E>> enumBind(key: String, default: E): Bind<E> = slot(key, default, serializer<E>(), emptyList()).bind

    @PublishedApi
    internal inline fun <reified E : Enum<E>> enumList(): List<E> = enumValues<E>().toList()

    /** A key or a mouse button, as `KeyBind` holds it. */
    fun keys(key: String, name: String, description: String, default: Int, dependsOn: Switch? = null): KeyBind =
        settings.keys(stored(key, default).bind, name, description, key, dependsOn)

    /** Stretches of a scale from 0 to [max], with a tick at each of [ticks]. */
    fun span(key: String, name: String, description: String, default: List<Section>, max: Int, ticks: List<Int> = emptyList()): Span =
        settings.span(stored(key, default).bind, name, description, max, ticks, key)

    /** Which of [E]'s values show, in the player's order, starting with [default]. */
    inline fun <reified E : Enum<E>> order(key: String, name: String, description: String, default: List<E>): Order<E> =
        order(slot(key, default, serializer<List<E>>(), emptyList()), name, description, enumValues<E>().toList())

    @PublishedApi
    internal fun <T> order(slot: Slot<List<T>>, name: String, description: String, values: List<T>): Order<T> =
        settings.order(slot.bind, name, description, values, slot.key)

    /** Gathers [rows], declared before it, under a bar that opens them, in this row's place. */
    fun fold(id: String, name: String, description: String, vararg rows: Setting): Fold {
        settings.take(rows.toList())
        return settings.fold(id, name, description) { for (row in rows) row(row) }
    }

    /** A button labeled [button] that runs [run], such as a reset of the rows around it. */
    fun action(id: String, name: String, description: String, button: String, run: () -> Unit) = settings.action(id, name, description, button, run = run)

    /** A picture on [canvas] of what the rows around it do, drawn by the edge. */
    fun preview(id: String, name: String, description: String, canvas: Canvas) = settings.preview(id, name, description, canvas)

    /** Declares rows with the kit's own `Rows` calls, for a row over a [Bind] of its own, such as one that spans several slots. */
    fun rows(declare: Rows.() -> Unit) = settings.declare()

    /** The entities this feature reads, as [readEntities] declared them. */
    var entityFilters: List<EntityFilter> = emptyList()
        private set

    internal val heldEntities = LinkedHashMap<Int, EntitySeen>()

    /**
     * The entities the client holds now that match this feature's [entityFilters], oldest first, each as its
     * [EntitySeen] with its latest name. A handler of an entity event sees them as they were before that event.
     */
    val entities: Collection<EntitySeen> get() = heldEntities.values

    /**
     * Reads the entities that match any of [filters], into [entities]. The edge's tracker walks the client's
     * entities only while a feature that reads them is on, and applies these before it reads a name, so keep each
     * one narrow. A handler of `EntitySeen`, `EntityRenamed` or `EntityGone` hears only the entities they match.
     */
    fun readEntities(vararg filters: EntityFilter) {
        check(entityFilters.isEmpty()) { "$id already reads entities" }
        entityFilters = filters.toList()
    }

    /** Handles every [E] the server sends. `now` is when it arrived, in epoch milliseconds. */
    inline fun <reified E : GameEvent> on(noinline handler: (event: E, now: Long) -> Unit) = on(E::class.java, handler)

    @PublishedApi
    internal fun <E : GameEvent> on(type: Class<E>, handler: (E, Long) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        check(handlers.put(type, handler as (GameEvent, Long) -> Unit) == null) { "$id already handles ${type.simpleName}" }
    }

    private class Line(val head: (String) -> Boolean, val read: (String, Long) -> Boolean)

    private val lines = ArrayList<Line>()

    /**
     * Handles each chat line that starts with [head]. A player's chat line starts with their name, so a server line
     * is told by its start, and a player can type anything after it. The first handler whose head matches takes the line.
     */
    fun line(head: String, handler: (now: Long) -> Unit) = line({ it.startsWith(head) }, { it }) { _, now -> handler(now) }

    /**
     * Handles each chat line that starts with [head] and parses it with [pattern], which matches the whole line. A
     * line whose head matches and whose pattern does not counts as [unparsed], which is how a format change shows up.
     */
    fun line(head: String, pattern: Regex, handler: (groups: MatchResult.Destructured, now: Long) -> Unit) =
        line({ it.startsWith(head) }, { pattern.matchEntire(it)?.destructured }, handler)

    /** As the [line] with a [String] head, for a server line whose start varies, such as a boss's name. [head] matches at the start. */
    fun line(head: Regex, pattern: Regex, handler: (groups: MatchResult.Destructured, now: Long) -> Unit) =
        line({ head.matchesAt(it, 0) }, { pattern.matchEntire(it)?.destructured }, handler)

    /** Handles each chat line that starts with [head], read by [parse], for a line a shared parser such as `Drop.parse` reads. Null is [unparsed]. */
    fun <T : Any> line(head: Regex, parse: (String) -> T?, handler: (T, now: Long) -> Unit) =
        line({ head.matchesAt(it, 0) }, parse, handler)

    private fun <T : Any> line(head: (String) -> Boolean, parse: (String) -> T?, handler: (T, Long) -> Unit) {
        if (lines.isEmpty()) {
            on<SystemMessage> { message, now ->
                val text = message.text.plain
                lines.firstOrNull { it.head(text) }?.let { if (!it.read(text, now)) unparsed(text) }
            }
        }
        lines += Line(head) { text, now -> parse(text)?.also { handler(it, now) } != null }
    }

    /**
     * Records that [sample], text that reached this feature's branch, failed to parse. The counter `<id>.unparsed`
     * is how a format change on the server shows up, and the sample goes to the player's report when they sent
     * one. Pass the server's text as it is, because the report redacts it. [line] does this for a chat line.
     */
    fun unparsed(sample: String) {
        metrics.count("$id.unparsed")
        metrics.onUnparsed?.invoke(id, sample)
    }

    /**
     * Keeps the chat line or the [FloatingText] this handler is reading out of the player's sight. Every feature still
     * sees it. The first time a feature that is on by default hides one, [Game] tells the player where its switch is.
     */
    fun hide() {
        hides = true
    }

    /** Shows [text] in place of the [FloatingText] this handler is reading, on this client only. */
    fun replace(text: Text) {
        replacement = text
    }

    internal var replacement: Text? = null

    /** Prints a line to chat that needs no action: this feature's name, then [detail]. Call it from a handler. */
    fun notice(detail: Text) {
        messages += Notice(title, detail)
    }

    /**
     * Shows [title] large in the middle of the screen with a sound, for something that needs the player
     * within seconds, and prints [detail] as [notice] does. Call it from a handler.
     */
    fun alert(title: Text, detail: Text) {
        messages += Alert(this.title, title, detail)
    }

    /**
     * The player's choice of where this feature's block docks beside the hotbar, for a feature that offers one. Dragging
     * the block sets it: into a dock spot, or out of one to [HudDock.FREE].
     */
    var placement: Choice<HudDock>? = null
        private set

    /** Offers the player [placement], saved under [key], for a feature whose block may dock beside the hotbar. */
    fun placement(key: String, description: String): Choice<HudDock> =
        choice(key, "Placement", description, HudDock.FREE).also { check(placement == null) { "$id already offers a placement" }; placement = it }

    /** Whether this feature ever shows a HUD block, which is whether it was given a [hud]. */
    val hasHud = hud != null

    /** A HUD block headed by [title], or with no header when [headed] is false, for rows that speak for themselves. */
    fun block(
        headed: Boolean = true,
        look: HudLook = HudLook.PLATE,
        dock: HudDock = HudDock.FREE,
        overMenus: Boolean = true,
        columns: Boolean = false,
        opacity: Float = 1f,
        pops: Boolean = false,
        icon: Item? = null,
        rows: HudBuilder.() -> Unit,
    ) = HudBlock(if (headed) header else text {}, HudBuilder().apply(rows).rows, look, dock, overMenus, columns, opacity, pops, icon)

    private companion object {
        val WORDS = mapOf("hud" to "HUD", "ui" to "UI", "dragsim" to "DragSim", "skyblock" to "SkyBlock")

        /** `protector-spawn-beam` as `protectorSpawnBeam`, the key a switch's field always had. */
        fun camel(id: String) = id.split('-').withIndex().joinToString("") { (i, word) -> if (i == 0) word else word.replaceFirstChar(Char::uppercase) }
    }
}
