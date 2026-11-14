package io.github.noxaur.dsmod.core.feature

import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.game.ContainerState
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.Metrics
import io.github.noxaur.dsmod.core.game.TabState
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.hud.HudBuilder
import io.github.noxaur.dsmod.core.text.Text
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.world.Mark
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.reflect.KMutableProperty0

/**
 * One thing the mod does for the player. A feature turns events into its own state in [on], and
 * turns that state into what the player sees in [hud], [slots] and [marks], says something once with [notice] and
 * [alert], or keeps a server line out of chat with [hide]. The shared layers draw and print all of it, so a feature touches no screen itself. [toggle] is its switch in the settings, and while that is off, none of these run.
 */
abstract class Feature(val id: String, private val toggle: KMutableProperty0<Boolean>) {
    /** The name the player knows the feature by: [id] in Title Case. */
    val title = id.split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    private val header = text { brand(title) }

    var enabled: Boolean
        get() = toggle.get()
        set(value) = toggle.set(value)

    internal val handlers = HashMap<Class<out GameEvent>, (GameEvent, Long) -> Unit>()
    internal lateinit var metrics: Metrics
    internal lateinit var tab: TabState
    internal lateinit var container: ContainerState
    internal lateinit var messages: MutableList<Message>
    internal var hides = false

    /** What this feature keeps between sessions, or null when it keeps nothing. The edge loads and writes it. */
    var state: Saved<*>? = null
        private set

    /** Declares the state that outlives the session. A feature has at most one. */
    protected inline fun <reified S : Any> saved(default: S) = saved(serializer<S>(), default)

    @PublishedApi
    internal fun <S : Any> saved(serializer: KSerializer<S>, default: S): Saved<S> {
        check(state == null) { "$id already saves state" }
        return Saved(serializer, default).also { state = it }
    }

    /** Handles every [E] the server sends. `now` is when it arrived, in epoch milliseconds. */
    protected inline fun <reified E : GameEvent> on(noinline handler: (event: E, now: Long) -> Unit) = on(E::class.java, handler)

    @PublishedApi
    internal fun <E : GameEvent> on(type: Class<E>, handler: (E, Long) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        check(handlers.put(type, handler as (GameEvent, Long) -> Unit) == null) { "$id already handles ${type.simpleName}" }
    }

    /** Adds to the counter `<id>.<name>`. Count a line that should have parsed and did not as `unparsed`. */
    protected fun count(name: String) = metrics.count("$id.$name")

    /** Keeps the chat line this handler is reading out of the player's chat. Every feature still sees the line. */
    protected fun hide() {
        hides = true
    }

    /** Prints a line to chat that needs no action: this feature's name, then [detail]. Call it from a handler. */
    protected fun notice(detail: Text) {
        messages += Notice(title, detail)
    }

    /**
     * Shows [title] large in the middle of the screen with a sound, for something that needs the player
     * within seconds, and prints [detail] as [notice] does. Call it from a handler.
     */
    protected fun alert(title: Text, detail: Text) {
        messages += Alert(this.title, title, detail)
    }

    /**
     * This feature's block on the HUD, built with [block], or null to show nothing. Called after an event
     * this feature could see and a few times a second, never per frame.
     */
    open fun hud(now: Long): HudBlock? = null

    /** Whether this feature ever shows a HUD block, which is whether it overrides [hud]. */
    val hasHud = javaClass.getMethod("hud", Long::class.javaPrimitiveType).declaringClass != Feature::class.java

    /** A HUD block headed by [title]. */
    protected fun block(rows: HudBuilder.() -> Unit) = HudBlock(header, HudBuilder().apply(rows).rows)

    /**
     * The marks on the open container, by slot index, or none. Called when [hud] is, so return the same
     * map until the marks change. A mark on a slot the open container lacks is not drawn.
     */
    open fun slots(now: Long): Map<Int, SlotMark> = emptyMap()

    /** The marks this feature draws in the world, or none. Called when [hud] is, so return equal marks until they change. */
    open fun marks(now: Long): List<Mark> = emptyList()
}
