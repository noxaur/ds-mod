package io.github.noxaur.dsmod.core.config

import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.ui.Colors
import io.github.noxaur.dsmod.core.ui.Look
import io.github.noxaur.dsmod.core.ui.Narration
import io.github.noxaur.dsmod.core.ui.Section
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

class Bind<T>(val field: String, val get: () -> T, val set: (T) -> Unit) {
    var follows: (() -> T?)? = null
        private set

    var formerly: List<String> = emptyList()
        private set

    fun formerly(vararg keys: String) = apply { formerly = keys.toList() }

    fun follows(read: () -> T?) = apply { follows = read }
}

enum class Kind(val spoken: String) {
    SWITCH("switch"),
    NUMBER("number"),
    CHOICE("choice"),
    PICK("pick"),
    COLOR("color"),
    KEYS("key bind"),
    SPAN("span"),
    FOLD("fold"),
    ACTION("button"),
    ORDER("list"),
    ITEMS("list"),
    PANEL("editor"),
    PREVIEW("preview"),
}

sealed class Setting(val id: String, val name: String, val description: String, val kind: Kind) {
    open val children: List<Setting> get() = emptyList()

    var gate: Switch? = null
        internal set

    var dependsOn: Switch? = null
        internal set

    var shownWhile: (() -> Boolean)? = null
        internal set

    val visible: Boolean get() = shownWhile?.invoke() != false

    val dimmedBy: Switch? get() = gate?.let { if (!it.value) it else it.dimmedBy } ?: dependsOn?.let { if (!it.value) it else it.dimmedBy }

    val dimmed: Boolean get() = dimmedBy != null

    open val changed: Boolean get() = false

    open fun reset() = Unit

    open fun summary(keys: (Int) -> String): String? = null

    open fun default(keys: (Int) -> String): String? = null

    protected open fun spoken(keys: (Int) -> String, open: Boolean): String? = null

    fun narration(keys: (Int) -> String, open: Boolean = false) = Narration.join(
        name,
        kind.spoken,
        spoken(keys, open),
        if (changed) default(keys)?.let { "changed from $it" } ?: "changed" else null,
        dimmedBy?.let { "dimmed, ${it.name} is off" },
    )

    open val labels: List<String> get() = emptyList()

    val searchText: String by lazy { (listOf(name, description) + labels).joinToString(" ").lowercase() }

    fun walk(): Sequence<Setting> = sequence {
        yield(this@Setting)
        for (child in children) yieldAll(child.walk())
    }
}

sealed class Valued<T>(id: String, name: String, description: String, kind: Kind, private val bind: Bind<T>) : Setting(id, name, description, kind) {
    val field get() = bind.field

    val formerly get() = bind.formerly

    var value: T
        get() = bind.get()
        set(next) = bind.set(normalize(next))

    var default: T = bind.get()
        private set
        get() = bind.follows?.invoke() ?: field

    protected open fun show(v: T, keys: (Int) -> String): String? = null

    override fun summary(keys: (Int) -> String) = show(value, keys)

    override fun default(keys: (Int) -> String) = show(default, keys)

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

class Switch(id: String, name: String, description: String, bind: Bind<Boolean>, children: List<Setting> = emptyList()) :
    Valued<Boolean>(id, name, description, Kind.SWITCH, bind) {
    override var children: List<Setting> = children
        internal set

    var feature: HasFeature? = null
        internal set

    fun toggle() {
        value = !value
    }

    fun closed(keys: (Int) -> String) = children.filter { it.visible }.mapNotNull { it.summary(keys) }.joinToString(", ")

    override fun default(keys: (Int) -> String) = if (default) "On" else "Off"

    override fun spoken(keys: (Int) -> String, open: Boolean) =
        Narration.join(if (value) "on" else "off", if (children.isEmpty()) null else if (open) "expanded" else "collapsed")
}

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
    val decimals = (0..3).first { abs(step * POWERS[it] - (step * POWERS[it]).roundToLong()) < 1e-4 || it == 3 }

    val zero = if (min < 0 && max > 0) 0f else min

    override fun normalize(next: Float): Float {
        if (next.isNaN()) return value
        val steps = ((next.coerceIn(min, max) - min) / step).roundToLong()
        return (((min + steps * step) * POWERS[decimals]).roundToLong() / POWERS[decimals]).toFloat().coerceIn(min, max)
    }

    fun step(by: Int) {
        value += by * step
    }

    fun fraction(v: Float) = (v - min) / (max - min)

    fun at(fraction: Double) {
        value = (min + fraction.coerceIn(0.0, 1.0) * (max - min)).toFloat()
    }

    fun format(v: Float = value): String = String.format(Locale.ROOT, "%.${decimals}f", v)

    fun parse(text: String): Float? = text.trim().removeSuffix(unit.trim()).trim().replace(',', '.').toFloatOrNull()?.let(::normalize)

    override fun show(v: Float, keys: (Int) -> String) = format(v) + unit

    override fun spoken(keys: (Int) -> String, open: Boolean) = show(value, keys)

    private companion object {
        val POWERS = doubleArrayOf(1.0, 10.0, 100.0, 1000.0)
    }
}

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

    val shown get() = label(value)

    override val labels: List<String> by lazy { values.map(label) }

    override fun show(v: T, keys: (Int) -> String) = label(v)

    override fun spoken(keys: (Int) -> String, open: Boolean) = Narration.join(shown, Narration.of(index, values.size))
}

class Pick<T>(
    id: String,
    name: String,
    description: String,
    bind: Bind<T>,
    values: List<T>,
    val look: (T) -> Look,
    label: (T) -> String = { it.toString() },
) : Choice<T>(id, name, description, Kind.PICK, bind, values, label) {
    fun lookAt(index: Int = this.index) = look(values[index])
}

class Color(id: String, name: String, description: String, bind: Bind<Int>) : Valued<Int>(id, name, description, Kind.COLOR, bind) {
    override fun show(v: Int, keys: (Int) -> String) = Colors.show(v)

    override fun spoken(keys: (Int) -> String, open: Boolean) = Colors.spoken(value)
}

class KeyBind(id: String, name: String, description: String, bind: Bind<Int>, val clicks: Boolean = true) : Valued<Int>(id, name, description, Kind.KEYS, bind) {
    fun clear() {
        value = NONE
    }

    override fun show(v: Int, keys: (Int) -> String) = keys(v)

    override fun spoken(keys: (Int) -> String, open: Boolean) = show(value, keys)

    companion object {
        const val NONE = -1
    }
}

class Span(id: String, name: String, description: String, bind: Bind<List<Section>>, val max: Int, val ticks: List<Int>) :
    Valued<List<Section>>(id, name, description, Kind.SPAN, bind) {
    override fun show(v: List<Section>, keys: (Int) -> String) = v.joinToString(", ") { "${it.from}–${it.to}" }.ifEmpty { "Never" }

    override fun spoken(keys: (Int) -> String, open: Boolean) = show(value, keys).replace("–", " to ")
}

class Fold(id: String, name: String, description: String, override val children: List<Setting>) : Setting(id, name, description, Kind.FOLD) {
    override val changed get() = children.any { it.changed }

    override fun reset() = children.forEach { it.reset() }

    override fun summary(keys: (Int) -> String) = children.filter { it.visible }.mapNotNull { it.summary(keys) }.joinToString(" · ")

    override fun spoken(keys: (Int) -> String, open: Boolean) = if (open) "expanded" else "collapsed"
}

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

    val entries: List<T>
        get() {
            val shown = value
            if (all.filter { it in shown } != shown) all = arrange(shown)
            return all
        }

    private fun arrange(shown: List<T>) = shown + values.filter { it !in shown }

    override fun normalize(next: List<T>) = next.distinct().filter { it in values }

    fun shown(index: Int) = entries[index] in value

    fun canSwitch(index: Int) = !shown(index) || value.size > least

    fun switch(index: Int) {
        if (!canSwitch(index)) return
        val entry = entries[index]
        val shown = value
        value = if (entry in shown) shown - entry else entries.filter { it in shown || it == entry }
    }

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

    fun restore(before: List<T>, shown: List<T>) {
        all = before
        value = shown
    }

    override val labels: List<String> by lazy { values.map(label) }

    override fun show(v: List<T>, keys: (Int) -> String) = v.joinToString(", ") { label(it) }

    override fun spoken(keys: (Int) -> String, open: Boolean) =
        Narration.join(show(value, keys), "${value.size} of ${values.size} shown", if (open) "expanded" else "collapsed")

    fun entryNarration(index: Int) = Narration.join(label(entries[index]), Kind.SWITCH.spoken, if (shown(index)) "on" else "off", Narration.of(index, entries.size))
}

class Action(id: String, name: String, description: String, val button: String, val run: () -> Unit) : Setting(id, name, description, Kind.ACTION) {
    override fun spoken(keys: (Int) -> String, open: Boolean) = button
}

@DslMarker
annotation class SettingsDsl

@SettingsDsl
class Rows {
    private val added = ArrayList<Setting>()

    val rows: List<Setting> get() = added

    private fun <S : Setting> add(setting: S, dependsOn: Switch?) = setting.also {
        it.dependsOn = dependsOn
        added += it
    }

    private fun nested(block: (Rows.() -> Unit)?) = if (block == null) emptyList() else Rows().apply(block).rows

    fun row(setting: Setting) = add(setting, null)

    internal fun take(rows: List<Setting>) {
        for (row in rows) check(added.remove(row)) { "${row.id} is not a row here" }
    }

    fun feature(feature: HasFeature): Switch = add(feature.feature.switch.also { it.feature = feature; it.children = feature.feature.rows }, null)

    fun switch(
        bind: Bind<Boolean>, name: String, description: String, id: String = bind.field.substringAfterLast('.'), dependsOn: Switch? = null,
        settings: (Rows.() -> Unit)? = null,
    ) = add(Switch(id, name, description, bind, nested(settings)), dependsOn)

    fun number(
        bind: Bind<Float>, name: String, description: String, range: ClosedFloatingPointRange<Float>, step: Float,
        unit: String = "", id: String = bind.field.substringAfterLast('.'), dependsOn: Switch? = null,
    ) = add(Number(id, name, description, bind, range.start, range.endInclusive, step, unit), dependsOn)

    fun <T> choice(bind: Bind<T>, name: String, description: String, values: List<T>, id: String = bind.field.substringAfterLast('.'), dependsOn: Switch? = null) =
        add(Choice(id, name, description, bind, values), dependsOn)

    fun <T> pick(
        bind: Bind<T>, name: String, description: String, values: List<T>, id: String = bind.field.substringAfterLast('.'), dependsOn: Switch? = null,
        label: (T) -> String = { it.toString() }, look: (T) -> Look,
    ) = add(Pick(id, name, description, bind, values, look, label), dependsOn)

    fun color(bind: Bind<Int>, name: String, description: String, id: String = bind.field.substringAfterLast('.'), dependsOn: Switch? = null) =
        add(Color(id, name, description, bind), dependsOn)

    fun keys(bind: Bind<Int>, name: String, description: String, id: String = bind.field.substringAfterLast('.'), dependsOn: Switch? = null, clicks: Boolean = true) =
        add(KeyBind(id, name, description, bind, clicks), dependsOn)

    fun span(
        bind: Bind<List<Section>>, name: String, description: String, max: Int, ticks: List<Int> = emptyList(),
        id: String = bind.field.substringAfterLast('.'), dependsOn: Switch? = null,
    ) = add(Span(id, name, description, bind, max, ticks), dependsOn)

    fun <T> order(bind: Bind<List<T>>, name: String, description: String, values: List<T>, id: String = bind.field.substringAfterLast('.'), dependsOn: Switch? = null) =
        add(Order(id, name, description, bind, values), dependsOn)

    fun shownWhile(condition: () -> Boolean, rows: Rows.() -> Unit) {
        for (row in nested(rows)) {
            row.shownWhile = condition
            added += row
        }
    }

    fun fold(id: String, name: String, description: String, dependsOn: Switch? = null, rows: Rows.() -> Unit) =
        add(Fold(id, name, description, nested(rows)), dependsOn)

    fun <T> items(bind: Bind<Grouped<T>>, name: String, description: String, kind: ItemKind<T>, id: String = bind.field.substringAfterLast('.'), dependsOn: Switch? = null) =
        add(Items(id, name, description, bind, kind), dependsOn)

    fun <T> panel(bind: Bind<T>, name: String, description: String, canvas: Canvas, id: String, dependsOn: Switch? = null, describe: (T) -> String) =
        add(Panel(id, name, description, bind, canvas, describe), dependsOn)

    fun preview(id: String, name: String, description: String, canvas: Canvas, dependsOn: Switch? = null) =
        add(Preview(id, name, description, canvas), dependsOn)

    fun action(id: String, name: String, description: String, button: String, dependsOn: Switch? = null, run: () -> Unit) =
        add(Action(id, name, description, button, run), dependsOn)

    fun action(opens: Opens, name: String, description: String, button: String = "Open", id: String, dependsOn: Switch? = null) =
        add(Action(id, name, description, button) { Opens.open(opens) }, dependsOn)
}

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
