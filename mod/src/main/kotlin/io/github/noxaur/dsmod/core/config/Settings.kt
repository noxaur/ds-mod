package io.github.noxaur.dsmod.core.config

import io.github.noxaur.dsmod.core.ui.Look
import io.github.noxaur.dsmod.core.ui.Narration
import io.github.noxaur.dsmod.core.ui.Section
import java.util.Locale
import kotlin.jvm.internal.CallableReference
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty0

/**
 * How a row reads and writes its value in `DsModConfig`. [field] names what it reaches, as `Class.field`, or
 * `Class.field.part` for a part of a value, and [also] any more fields one value spans, such as a list's groups, so
 * a test can check that every saved field has exactly one row.
 */
class Bind<T>(val field: String, val get: () -> T, val set: (T) -> Unit) {
    var also: List<String> = emptyList()
        private set

    /**
     * The default while the row shows a look rather than the fresh config's value, such as a preset's border.
     * Null keeps the default taken from a fresh config.
     */
    var follows: (() -> T?)? = null
        private set

    /** Names the other saved [fields] this one value spans. */
    fun spanning(vararg fields: String) = apply { also = fields.toList() }

    /** The changed marker and a reset use [read] while it has a value, and the fresh config's otherwise. */
    fun follows(read: () -> T?) = apply { follows = read }

    companion object {
        fun <T> of(property: KMutableProperty0<T>) = Bind(field(property), property::get, property::set)

        /** `Class.field` of a property bound to its object, such as `config.end::dragonTimer`. */
        fun field(property: KProperty0<*>) = "${(property as CallableReference).boundReceiver.javaClass.simpleName}.${property.name}"
    }
}

/** The kinds of row, each with the word the narrator calls it. The edge has one face per kind, and the kit gallery shows every one. */
enum class Kind(val spoken: String) {
    SWITCH("switch"),
    NUMBER("number"),
    CHOICE("choice"),
    PICK("pick"),
    KEYS("key bind"),
    SPAN("span"),
    FOLD("fold"),
    ACTION("button"),
    ORDER("list"),
    ITEMS("list"),
    PANEL("editor"),
    PREVIEW("preview"),
}

/**
 * One row of the settings screen. Every row has a stable [id], a [name] and a [description], and gets the same
 * behaviors whatever its kind: a changed marker that resets, dimming under an off switch, a summary when its parent
 * is closed, search text, and the keyboard. `settings.md` beside the ui skill says how to add one.
 */
sealed class Setting(val id: String, val name: String, val description: String, val kind: Kind) {
    /** The rows that open under this one: a switch's own settings, or a fold's. */
    open val children: List<Setting> get() = emptyList()

    /** The switch this row sits under: its parent's, or its group's heading switch. The tree sets it. */
    var gate: Switch? = null
        internal set

    /** A switch elsewhere that this row needs, which dims it while off. */
    var dependsOn: Switch? = null
        internal set

    /** The switch that is off and dims this row: the one it sits under first, then the one it depends on, or a switch above either. */
    val dimmedBy: Switch? get() = gate?.let { if (!it.value) it else it.dimmedBy } ?: dependsOn?.let { if (!it.value) it else it.dimmedBy }

    /** A row under an off switch dims, and stays editable so it can be set up first. */
    val dimmed: Boolean get() = dimmedBy != null

    /** Whether the value differs from its default, which shows the changed marker. */
    open val changed: Boolean get() = false

    open fun reset() = Unit

    /** The value in a few characters, for a closed parent's summary, or null to leave this row out of it. */
    open fun summary(keys: (Int) -> String): String? = null

    /** The default, as the changed marker's tooltip names it, or null when the row has none of its own. */
    open fun default(keys: (Int) -> String): String? = null

    /** The value as the narrator says it after the kind, such as `off` or `All, 1 of 3`. [open] is whether its rows show. */
    protected open fun spoken(keys: (Int) -> String, open: Boolean): String? = null

    /**
     * What the narrator says for this row: its name, its kind and value, then whether it differs from its default
     * and which switch dims it, as "Distance, number, 7.5, changed from 4.0, dimmed, Camera is off".
     */
    fun narration(keys: (Int) -> String, open: Boolean = false) = Narration.join(
        name,
        kind.spoken,
        spoken(keys, open),
        if (changed) default(keys)?.let { "changed from $it" } ?: "changed" else null,
        dimmedBy?.let { "dimmed, ${it.name} is off" },
    )

    /** Words besides the name and the description that search matches, such as a choice's values. */
    open val labels: List<String> get() = emptyList()

    /** Everything search matches, lowercased. */
    val searchText: String by lazy { (listOf(name, description) + labels).joinToString(" ").lowercase() }

    /** This row and every row under it, in order. */
    fun walk(): Sequence<Setting> = sequence {
        yield(this@Setting)
        for (child in children) yieldAll(child.walk())
    }

    companion object {
        /**
         * The most characters a description may have, so the settings screen shows a row's two lines of it at full
         * width and a reader takes it in at a glance. `SettingsTreeTest` holds every row to it, and the player docs
         * hold the rest.
         */
        const val DESCRIPTION_MAX = 67
    }
}

/** A row with a value of its own, bound to `DsModConfig`. Its default is its value in a fresh config, which the tree sets. */
sealed class Valued<T>(id: String, name: String, description: String, kind: Kind, private val bind: Bind<T>) : Setting(id, name, description, kind) {
    val field get() = bind.field

    /** Every field the row reaches: its own, and the others its value spans. */
    val fields get() = listOf(bind.field) + bind.also

    var value: T
        get() = bind.get()
        set(next) = bind.set(normalize(next))

    /**
     * The value a reset returns to. A row that [Bind.follows] a look on screen uses that while it has one, and
     * otherwise the value from a fresh config, which [adopt] stores.
     */
    var default: T = bind.get()
        private set
        get() = bind.follows?.invoke() ?: field

    /** Takes the default from the same row of a tree built on a fresh config. */
    @Suppress("UNCHECKED_CAST")
    internal fun adopt(fresh: Setting) {
        default = (fresh as Valued<T>).value
    }

    protected open fun normalize(next: T) = next

    override val changed get() = value != default

    override fun reset() {
        value = default
    }
}

/** On or off. Its [children] are its own settings, which open under it and dim while it is off. */
class Switch(id: String, name: String, description: String, bind: Bind<Boolean>, override val children: List<Setting> = emptyList()) :
    Valued<Boolean>(id, name, description, Kind.SWITCH, bind) {
    fun toggle() {
        value = !value
    }

    /** What the closed settings hold, such as `L, K`, shown before the switch. */
    fun closed(keys: (Int) -> String) = children.mapNotNull { it.summary(keys) }.joinToString(", ")

    override fun default(keys: (Int) -> String) = if (default) "On" else "Off"

    override fun spoken(keys: (Int) -> String, open: Boolean) =
        Narration.join(if (value) "on" else "off", if (children.isEmpty()) null else if (open) "expanded" else "collapsed")
}

/**
 * A number from [min] to [max] in steps of [step], counted from [min], shown with [unit] after it. A range that
 * runs through zero fills from zero.
 */
class Number(
    id: String,
    name: String,
    description: String,
    bind: Bind<Float>,
    val min: Float,
    val max: Float,
    val step: Float,
    val unit: String = "",
) : Valued<Float>(id, name, description, Kind.NUMBER, bind) {
    /** As many decimals as the step has. */
    val decimals = (0..3).first { abs(step * POWERS[it] - (step * POWERS[it]).roundToLong()) < 1e-4 || it == 3 }

    /** Where the fill starts: zero for a signed range, else the minimum. */
    val zero = if (min < 0 && max > 0) 0f else min

    override fun normalize(next: Float): Float {
        if (next.isNaN()) return value
        val steps = ((next.coerceIn(min, max) - min) / step).roundToLong()
        return (((min + steps * step) * POWERS[decimals]).roundToLong() / POWERS[decimals]).toFloat().coerceIn(min, max)
    }

    /** Steps [by] steps, so Shift can take ten. */
    fun step(by: Int) {
        value += by * step
    }

    /** How far along the track [v] is, from 0 to 1. */
    fun fraction(v: Float) = (v - min) / (max - min)

    /** Jumps to the value [fraction] of the way along the track. */
    fun at(fraction: Double) {
        value = (min + fraction.coerceIn(0.0, 1.0) * (max - min)).toFloat()
    }

    fun format(v: Float = value): String = String.format(Locale.ROOT, "%.${decimals}f", v)

    /** The value [text] means, such as `4.5`, `-12°` or `250 ms`, or null when it is not a number. */
    fun parse(text: String): Float? = text.trim().removeSuffix(unit.trim()).trim().replace(',', '.').toFloatOrNull()?.let(::normalize)

    override fun summary(keys: (Int) -> String) = format() + unit

    override fun default(keys: (Int) -> String) = format(default) + unit

    override fun spoken(keys: (Int) -> String, open: Boolean) = format() + unit

    private companion object {
        val POWERS = doubleArrayOf(1.0, 10.0, 100.0, 1000.0)
    }
}

/** One of a few [values], each shown as its [label]. Stepping wraps at either end. */
open class Choice<T> internal constructor(
    id: String,
    name: String,
    description: String,
    kind: Kind,
    bind: Bind<T>,
    val values: List<T>,
    val label: (T) -> String,
) : Valued<T>(id, name, description, kind, bind) {
    constructor(id: String, name: String, description: String, bind: Bind<T>, values: List<T>, label: (T) -> String = { it.toString() }) :
        this(id, name, description, Kind.CHOICE, bind, values, label)

    val index get() = values.indexOf(value).coerceAtLeast(0)

    fun step(by: Int) = choose(Math.floorMod(index + by, values.size))

    fun choose(index: Int) {
        value = values[index]
    }

    /** The current value's label. */
    val shown get() = label(value)

    override val labels: List<String> by lazy { values.map(label) }

    override fun summary(keys: (Int) -> String) = label(value)

    override fun default(keys: (Int) -> String) = label(default)

    override fun spoken(keys: (Int) -> String, open: Boolean) = Narration.join(shown, Narration.of(index, values.size))
}

/** A choice made by how each value looks: its dropdown and its menu show the [look] of each. */
class Pick<T>(
    id: String,
    name: String,
    description: String,
    bind: Bind<T>,
    values: List<T>,
    val look: (T) -> Look,
    label: (T) -> String = { it.toString() },
) : Choice<T>(id, name, description, Kind.PICK, bind, values, label) {
    /** The look of the value at [index], or of the current one. */
    fun lookAt(index: Int = this.index) = look(values[index])
}

/** A key or a mouse button, as a GLFW code, a mouse button from 0 to 9, or [NONE]. */
class KeyBind(id: String, name: String, description: String, bind: Bind<Int>) : Valued<Int>(id, name, description, Kind.KEYS, bind) {
    fun clear() {
        value = NONE
    }

    override fun summary(keys: (Int) -> String) = keys(value)

    override fun default(keys: (Int) -> String) = keys(default)

    override fun spoken(keys: (Int) -> String, open: Boolean) = keys(value)

    companion object {
        const val NONE = -1
    }
}

/** Stretches of a scale from 0 to [max], with a tick at each of [ticks]. `Sections` holds the rules of a stretch. */
class Span(id: String, name: String, description: String, bind: Bind<List<Section>>, val max: Int, val ticks: List<Int>) :
    Valued<List<Section>>(id, name, description, Kind.SPAN, bind) {
    override fun summary(keys: (Int) -> String) = text(value)

    override fun default(keys: (Int) -> String) = text(default)

    // The en dash of a stretch is read as a word.
    override fun spoken(keys: (Int) -> String, open: Boolean) = text(value).replace("–", " to ")

    private fun text(sections: List<Section>) = sections.joinToString(", ") { "${it.from}–${it.to}" }.ifEmpty { "Never" }
}

/** More rows the player rarely needs, under a bar that opens them. It is changed when any of them is, and resets them all. */
class Fold(id: String, name: String, description: String, override val children: List<Setting>) : Setting(id, name, description, Kind.FOLD) {
    override val changed get() = children.any { it.changed }

    override fun reset() = children.forEach { it.reset() }

    override fun summary(keys: (Int) -> String) = children.mapNotNull { it.summary(keys) }.joinToString(" · ")

    override fun spoken(keys: (Int) -> String, open: Boolean) = if (open) "expanded" else "collapsed"
}

/**
 * Which of [values] show, and in what order: the value is the shown ones in order. The list the player sees holds
 * every value, and a hidden one keeps the place it was given while this row lives, so showing it again puts it back
 * where it was. At least [least] stay shown.
 */
class Order<T>(
    id: String,
    name: String,
    description: String,
    bind: Bind<List<T>>,
    val values: List<T>,
    val label: (T) -> String = { it.toString() },
    val least: Int = 1,
) : Valued<List<T>>(id, name, description, Kind.ORDER, bind) {
    private var all = arrange(value)

    /** Every value in the order the list shows: the shown ones in their order, the hidden ones where they were left. */
    val entries: List<T>
        get() {
            val shown = value
            // A change from outside, such as a reset, rebuilds the list around it.
            if (all.filter { it in shown } != shown) all = arrange(shown)
            return all
        }

    private fun arrange(shown: List<T>) = shown + values.filter { it !in shown }

    override fun normalize(next: List<T>) = next.distinct().filter { it in values }

    fun shown(index: Int) = entries[index] in value

    /** Whether the entry at [index] can be switched now: a hidden one always, a shown one while more than [least] show. */
    fun canSwitch(index: Int) = !shown(index) || value.size > least

    /** Shows or hides the entry at [index] without moving it. */
    fun switch(index: Int) {
        if (!canSwitch(index)) return
        val entry = entries[index]
        val shown = value
        value = if (entry in shown) shown - entry else entries.filter { it in shown || it == entry }
    }

    /** Moves the entry at [from] to [to], kept within the list, and returns where it went. */
    fun move(from: Int, to: Int): Int {
        val list = entries.toMutableList()
        val at = to.coerceIn(0, list.lastIndex)
        if (at == from) return from
        list.add(at, list.removeAt(from))
        val shown = value
        all = list
        value = list.filter { it in shown }
        return at
    }

    /** Puts the list back as [before] held it, as Escape does after a drag. */
    fun restore(before: List<T>, shown: List<T>) {
        all = before
        value = shown
    }

    private fun text(list: List<T>) = list.joinToString(", ") { label(it) }

    override val labels: List<String> by lazy { values.map(label) }

    override fun summary(keys: (Int) -> String) = text(value)

    override fun default(keys: (Int) -> String) = text(default)

    override fun spoken(keys: (Int) -> String, open: Boolean) =
        Narration.join(text(value), "${value.size} of ${values.size} shown", if (open) "expanded" else "collapsed")

    /** What the narrator says for the entry at [index]: its name, its switch, and its place in the list. */
    fun entryNarration(index: Int) = Narration.join(label(entries[index]), Kind.SWITCH.spoken, if (shown(index)) "on" else "off", Narration.of(index, entries.size))
}

/** A button that runs [run], such as opening an editor screen that returns to the settings. [button] is its label. */
class Action(id: String, name: String, description: String, val button: String, val run: () -> Unit) : Setting(id, name, description, Kind.ACTION) {
    override fun spoken(keys: (Int) -> String, open: Boolean) = button
}

@DslMarker
annotation class SettingsDsl

/** Declares rows. Each call adds one row in order, and a row's own settings are declared in its trailing block. */
@SettingsDsl
class Rows {
    private val added = ArrayList<Setting>()

    /** The rows declared so far, in order. */
    val rows: List<Setting> get() = added

    private fun <S : Setting> add(setting: S, dependsOn: Switch?) = setting.also {
        it.dependsOn = dependsOn
        added += it
    }

    private fun nested(block: (Rows.() -> Unit)?) = if (block == null) emptyList() else Rows().apply(block).rows

    fun switch(
        property: KMutableProperty0<Boolean>, name: String, description: String, id: String = property.name, dependsOn: Switch? = null,
        settings: (Rows.() -> Unit)? = null,
    ) = switch(Bind.of(property), name, description, id, dependsOn, settings)

    fun switch(
        bind: Bind<Boolean>, name: String, description: String, id: String = bind.field.substringAfterLast('.'), dependsOn: Switch? = null,
        settings: (Rows.() -> Unit)? = null,
    ) = add(Switch(id, name, description, bind, nested(settings)), dependsOn)

    fun number(
        property: KMutableProperty0<Float>, name: String, description: String, range: ClosedFloatingPointRange<Float>, step: Float,
        unit: String = "", id: String = property.name, dependsOn: Switch? = null,
    ) = add(Number(id, name, description, Bind.of(property), range.start, range.endInclusive, step, unit), dependsOn)

    inline fun <reified E : Enum<E>> choice(
        property: KMutableProperty0<E>, name: String, description: String, id: String = property.name, dependsOn: Switch? = null,
    ) = choice(Bind.of(property), name, description, enumValues<E>().toList(), id, dependsOn)

    fun <T> choice(bind: Bind<T>, name: String, description: String, values: List<T>, id: String = bind.field.substringAfterLast('.'), dependsOn: Switch? = null) =
        add(Choice(id, name, description, bind, values), dependsOn)

    inline fun <reified E : Enum<E>> pick(
        property: KMutableProperty0<E>, name: String, description: String, id: String = property.name, noinline look: (E) -> Look,
    ) = pick(Bind.of(property), name, description, enumValues<E>().toList(), id, label = { it.toString() }, look = look)

    fun <T> pick(
        bind: Bind<T>, name: String, description: String, values: List<T>, id: String = bind.field.substringAfterLast('.'), dependsOn: Switch? = null,
        label: (T) -> String = { it.toString() }, look: (T) -> Look,
    ) = add(Pick(id, name, description, bind, values, look, label), dependsOn)

    fun keys(property: KMutableProperty0<Int>, name: String, description: String, id: String = property.name, dependsOn: Switch? = null) =
        add(KeyBind(id, name, description, Bind.of(property)), dependsOn)

    fun span(
        property: KMutableProperty0<List<Section>>, name: String, description: String, max: Int, ticks: List<Int> = emptyList(),
        id: String = property.name, dependsOn: Switch? = null,
    ) = add(Span(id, name, description, Bind.of(property), max, ticks), dependsOn)

    /** An ordered list of [E]'s values that the player shows, hides and drags into order. */
    inline fun <reified E : Enum<E>> order(
        property: KMutableProperty0<MutableList<E>>, name: String, description: String, id: String = property.name, dependsOn: Switch? = null,
    ) = order(Bind(Bind.field(property), { property.get().toList() }) { property.set(it.toMutableList()) }, name, description, enumValues<E>().toList(), id, dependsOn)

    fun <T> order(bind: Bind<List<T>>, name: String, description: String, values: List<T>, id: String = bind.field.substringAfterLast('.'), dependsOn: Switch? = null) =
        add(Order(id, name, description, bind, values), dependsOn)

    fun fold(id: String, name: String, description: String, dependsOn: Switch? = null, rows: Rows.() -> Unit) =
        add(Fold(id, name, description, nested(rows)), dependsOn)

    /** A list the player builds of [kind]'s items, under groups, edited inline. */
    fun <T> items(bind: Bind<Grouped<T>>, name: String, description: String, kind: ItemKind<T>, id: String = bind.field.substringAfterLast('.'), dependsOn: Switch? = null) =
        add(Items(id, name, description, bind, kind), dependsOn)

    /** An editor drawn as a picture of what it edits, on the edge's [canvas], summed up by [describe]. */
    fun <T> panel(bind: Bind<T>, name: String, description: String, canvas: Canvas, id: String, dependsOn: Switch? = null, describe: (T) -> String) =
        add(Panel(id, name, description, bind, canvas, describe), dependsOn)

    /** A picture on [canvas] of what the rows around it do, which holds no value. */
    fun preview(id: String, name: String, description: String, canvas: Canvas, dependsOn: Switch? = null) =
        add(Preview(id, name, description, canvas), dependsOn)

    /** A button that runs [run], such as a reset of a group. */
    fun action(id: String, name: String, description: String, button: String, dependsOn: Switch? = null, run: () -> Unit) =
        add(Action(id, name, description, button, run), dependsOn)

    /** A button that opens the edge's screen [opens], so the tree needs no Minecraft. */
    fun action(opens: Opens, name: String, description: String, button: String = "Open", id: String, dependsOn: Switch? = null) =
        add(Action(id, name, description, button) { Opens.open(opens) }, dependsOn)
}

/**
 * A screen of the edge's that an action row opens: one that needs the game behind it, such as Move HUD, or one that
 * is not a setting, such as Contribute's requests. `DsModClient` registers what opens each, as it gives each [Canvas]
 * its face.
 */
enum class Opens {
    HUD_EDITOR,
    REQUESTS,
    ;

    companion object {
        private val screens = HashMap<Opens, () -> Unit>()

        fun register(opens: Opens, open: () -> Unit) {
            screens[opens] = open
        }

        fun open(opens: Opens) = screens[opens]?.invoke() ?: Unit
    }
}
