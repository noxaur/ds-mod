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

interface HasFeature {
    val feature: Feature
}

fun interface Handler<in E : GameEvent> {
    fun handle(event: E, now: Long)
}

/** @keep Start from the feature closest to yours. Chat lines: DragonTimer. The tab list's info rows, or the sidebar's
 * the same way: ProtectorStatus. Items and slot marks: ItemRarity. Items on the HUD, from the player's own
 * inventory or an entity near them: EquipmentDisplay. Changing how the game draws, reading nothing from the
 * server: Fullbright. Counting something and keeping the total: SlayerTracker. Hiding a line the server sends:
 * HideCooldownMessages. Shortening or hiding text the server floats in the world: DamageNumbers. Telling the
 * player something once, with a setting besides the switch: RareDropAlert. A rate, following the player into
 * and out of its area: ZealotTracker. Marking a place in the world: ProtectorSpawnBeam. Reading the entities
 * the client holds and marking them: BossBeacon, with its part BossMarks. A key the player binds, or the
 * camera: FreeLook and Camera, with Cameras in edge and the rules in core/camera. The player's own input on a
 * vanilla screen: CopyChat, with CopyChats in edge. Settings the edge reads each frame, and a button that
 * resets them: Animations, with HeldItems in edge */
class Feature(
    config: DsModConfig,
    val id: String,
    val area: Area,
    val store: Store,
    description: String,
    default: Boolean = true,
    formerly: List<String> = emptyList(),
    // @keep Called after an event this feature could see and a few times a second, never per frame
    internal val hud: ((now: Long) -> HudBlock?)? = null,
    // @keep Called when hud is, so return the same map until the marks change
    internal val slots: ((now: Long) -> Map<Int, SlotMark>)? = null,
    // @keep Called when hud is, so return equal marks until they change
    internal val marks: ((now: Long) -> List<Mark>)? = null,
) {
    val title = id.split('-').joinToString(" ") { WORDS[it] ?: it.replaceFirstChar(Char::uppercase) }
    private val header = text { brand(title) }

    private val stored = config.stored(store)

    @PublishedApi
    internal val settings = Rows()

    // @keep The switch is saved under the id in camel case, as `protectorSpawnBeam`, the key its field always had
    val switch: Switch = Switch(id, title, description, slot(camel(id), default, serializer(), formerly).bind)

    val rows: List<Setting> get() = settings.rows

    var enabled: Boolean
        get() = switch.value && !switch.dimmed
        set(value) {
            switch.value = value
        }

    /** @keep Code outside core asks this or runs and never the switch, because off dragsim.co a switch that is on still
     * runs nothing */
    val on: Boolean get() = enabled && gate()

    val runs: Boolean get() = on && inArea()

    internal var gate: () -> Boolean = { true }

    internal val handlers = HashMap<Class<out GameEvent>, Handler<GameEvent>>()
    internal lateinit var metrics: Metrics
    internal lateinit var tab: InfoRows

    internal lateinit var sidebar: InfoRows
    internal lateinit var inArea: () -> Boolean
    internal lateinit var container: ContainerState
    internal lateinit var messages: MutableList<Message>
    internal var hides = false

    val worldNumber: Int get() = worlds()

    internal var worlds: () -> Int = { 0 }

    val here: Boolean get() = inArea()

    var state: Saved<*>? = null
        private set

    inline fun <reified S : Any> saved(default: S) = saved(serializer<S>(), default)

    @PublishedApi
    internal fun <S : Any> saved(serializer: KSerializer<S>, default: S): Saved<S> {
        check(state == null) { "$id already saves state" }
        return Saved(serializer, default).also { state = it }
    }

    private val inWorld = ArrayList<Scoped<*>>()
    private val inSession = ArrayList<Scoped<*>>()

    fun <S> world(initial: () -> S) = Scoped(initial).also { inWorld += it }

    fun <S> session(initial: () -> S) = Scoped(initial).also { inSession += it }

    internal fun forget(event: GameEvent) {
        if (event != WorldChanged && event != LeftServer) return
        for (scoped in inWorld) scoped.reset()
        if (event == LeftServer) for (scoped in inSession) scoped.reset()
    }

    inline fun <reified T> stored(key: String, default: T, vararg formerly: String) = slot(key, default, serializer<T>(), formerly.toList())

    @PublishedApi
    internal fun <T> slot(key: String, default: T, serializer: KSerializer<T>, formerly: List<String>): Slot<T> = stored.slot(key, default, serializer, formerly)

    inline fun <reified T> former(key: String): T? = former(key, serializer<T>())

    @PublishedApi
    internal fun <T> former(key: String, serializer: KSerializer<T>): T? = stored.former(key, serializer)

    // @keep `under` takes rows declared before the switch from their place to open under it, as a fold does
    fun switch(
        key: String, name: String, description: String, default: Boolean, dependsOn: Switch? = null, formerly: List<String> = emptyList(),
        under: List<Setting> = emptyList(), settings: (Rows.() -> Unit)? = null,
    ): Switch {
        this.settings.take(under)
        val rows: (Rows.() -> Unit)? = if (under.isEmpty()) settings else ({ for (row in under) row(row); settings?.invoke(this) })
        return this.settings.switch(slot(key, default, serializer(), formerly).bind, name, description, key, dependsOn, rows)
    }

    fun number(
        key: String, name: String, description: String, default: Float, range: ClosedFloatingPointRange<Float>, step: Float, unit: String = "",
        dependsOn: Switch? = null,
    ): Number = settings.number(stored(key, default).bind, name, description, range, step, unit, key, dependsOn)

    inline fun <reified E : Enum<E>> choice(key: String, name: String, description: String, default: E, dependsOn: Switch? = null, formerly: List<String> = emptyList()): Choice<E> =
        settings.choice(enumBind(key, default, formerly), name, description, enumList<E>(), key, dependsOn)

    inline fun <reified E : Enum<E>> pick(key: String, name: String, description: String, default: E, noinline look: (E) -> Look): Pick<E> =
        settings.pick(enumBind(key, default), name, description, enumList<E>(), key, look = look)

    @PublishedApi
    internal inline fun <reified E : Enum<E>> enumBind(key: String, default: E, formerly: List<String> = emptyList()): Bind<E> = slot(key, default, serializer<E>(), formerly).bind

    @PublishedApi
    internal inline fun <reified E : Enum<E>> enumList(): List<E> = enumValues<E>().toList()

    fun keys(
        key: String, name: String, description: String, default: Int, dependsOn: Switch? = null, clicks: Boolean = true, formerly: List<String> = emptyList(),
        refuses: (Int) -> Boolean = { false },
    ): KeyBind {
        val bind = slot(key, default, serializer(), formerly).bind
        return settings.keys(Bind(bind.field, bind.get) { if (!refuses(it)) bind.set(it) }.formerly(*formerly.toTypedArray()), name, description, key, dependsOn, clicks)
    }

    fun span(key: String, name: String, description: String, default: List<Section>, max: Int, ticks: List<Int> = emptyList()): Span =
        settings.span(stored(key, default).bind, name, description, max, ticks, key)

    inline fun <reified E : Enum<E>> order(key: String, name: String, description: String, default: List<E>): Order<E> =
        order(slot(key, default, serializer<List<E>>(), emptyList()), name, description, enumValues<E>().toList())

    @PublishedApi
    internal fun <T> order(slot: Slot<List<T>>, name: String, description: String, values: List<T>): Order<T> =
        settings.order(slot.bind, name, description, values, slot.key)

    fun fold(id: String, name: String, description: String, vararg rows: Setting): Fold {
        settings.take(rows.toList())
        return settings.fold(id, name, description) { for (row in rows) row(row) }
    }

    fun action(id: String, name: String, description: String, button: String, run: () -> Unit) = settings.action(id, name, description, button, run = run)

    fun preview(id: String, name: String, description: String, canvas: Canvas) = settings.preview(id, name, description, canvas)

    fun rows(declare: Rows.() -> Unit) = settings.declare()

    var entityFilters: List<EntityFilter> = emptyList()
        private set

    internal val heldEntities = LinkedHashMap<Int, EntitySeen>()

    // @keep A handler of an entity event sees the entities as they were before that event
    val entities: Collection<EntitySeen> get() = heldEntities.values

    /** @keep Keep each filter narrow: the edge's tracker walks the client's entities only while a feature that reads them
     * is on */
    fun readEntities(vararg filters: EntityFilter) {
        check(entityFilters.isEmpty()) { "$id already reads entities" }
        entityFilters = filters.toList()
    }

    inline fun <reified E : GameEvent> on(handler: Handler<E>) = on(E::class.java, handler)

    @PublishedApi
    internal fun <E : GameEvent> on(type: Class<E>, handler: Handler<E>) {
        @Suppress("UNCHECKED_CAST")
        handlers.merge(type, handler as Handler<GameEvent>) { first, then ->
            Handler { event, now ->
                first.handle(event, now)
                then.handle(event, now)
            }
        }
    }

    private class Line(val head: (String) -> Boolean, val read: (String, Long) -> Boolean)

    private val lines = ArrayList<Line>()

    /** @keep A player's chat line starts with their name, so a server line is told by its start, and a player can type
     * anything after it */
    fun line(head: String, handler: (now: Long) -> Unit) = line({ it.startsWith(head) }, { it }) { _, now -> handler(now) }

    fun line(head: String, pattern: Regex, handler: (groups: MatchResult.Destructured, now: Long) -> Unit) =
        line({ it.startsWith(head) }, { pattern.matchEntire(it)?.destructured }, handler)

    fun line(head: Regex, pattern: Regex, handler: (groups: MatchResult.Destructured, now: Long) -> Unit) =
        line({ head.matchesAt(it, 0) }, { pattern.matchEntire(it)?.destructured }, handler)

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

    // @keep Pass the server's text as it is, because the report redacts it
    fun unparsed(sample: String) {
        metrics.count("$id.unparsed")
        metrics.onUnparsed?.invoke(id, sample)
    }

    fun hide() {
        hides = true
    }

    fun replace(text: Text) {
        replacement = text
    }

    internal var replacement: Text? = null

    fun notice(detail: Text) {
        messages += Notice(title, detail)
    }

    fun alert(title: Text, detail: Text) {
        messages += Alert(this.title, title, detail)
    }

    var placement: Choice<HudDock>? = null
        private set

    fun placement(key: String, description: String): Choice<HudDock> =
        choice(key, "Placement", description, HudDock.FREE).also { check(placement == null) { "$id already offers a placement" }; placement = it }

    val hasHud = hud != null

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
        val WORDS = mapOf("hud" to "HUD", "ui" to "UI", "dragsim" to "DragSim", "skyblock" to "SkyBlock", "hotd" to "HOTD")
    }
}

internal fun camel(id: String) = id.split('-').withIndex().joinToString("") { (i, word) -> if (i == 0) word else word.replaceFirstChar(Char::uppercase) }
