package io.github.noxaur.dsmod.core.feature

import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerState
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.Metrics
import io.github.noxaur.dsmod.core.game.InfoRows
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.hud.HudBuilder
import io.github.noxaur.dsmod.core.hud.HudDock
import io.github.noxaur.dsmod.core.hud.HudLook
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
 * Outside [area] the feature shows nothing and sees only the tab list, the sidebar and a change of world.
 *
 * A feature is listed in [features], or it never runs. Start from the feature closest to yours:
 * - Follow lines in chat: `DragonTimer`.
 * - Read the tab list's info rows, or the sidebar's the same way: `GolemStatus`.
 * - Read items and mark slots: `RarityMarks`.
 * - Show items on the HUD, from the player's own inventory or an entity near them: `EquipmentDisplay`.
 * - Change how the game draws, reading nothing from the server: `Fullbright`.
 * - Count something and keep the total: `SlayerTracker`.
 * - Hide a line the server sends, and tell the player once that you did: `AbilitySpam`.
 * - Tell the player something once, and read a setting besides the switch: `LootAlerts`.
 * - Track a rate, and follow the player into and out of its area: `ZealotTracker`.
 * - Mark a place in the world: `BossBeacon`.
 * - Act on a key the player binds, or change the camera: `FreeLook` and `Camera`, with `Cameras` in `edge` and the
 *   rules in `core/camera`.
 * - Handle the player's own input on a vanilla screen, reading nothing from the server: `CopyChat`, with `CopyChats`
 *   in `edge`.
 */
abstract class Feature(val id: String, private val toggle: KMutableProperty0<Boolean>, val area: Area) {
    /** The name the player knows the feature by: [id] in Title Case. */
    val title = id.split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    private val header = text { brand(title) }

    var enabled: Boolean
        get() = toggle.get()
        set(value) = toggle.set(value)

    /**
     * Whether the feature runs now: its switch is on and the mod is active on this server. Code outside `core` asks
     * this and never the switch, because off dragsim.co a switch that is on still runs nothing.
     */
    val runs: Boolean get() = enabled && gate()

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

    /** Whether the player is in [area], for a tab-list or sidebar handler that follows them arriving and leaving. */
    protected val here: Boolean get() = inArea()

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

    /**
     * Records that [sample], text that reached this feature's branch, failed to parse. The counter `<id>.unparsed`
     * is how a format change on the server shows up, and the sample goes to the player's report when they sent
     * one. Pass the server's text as it is, because the report redacts it.
     */
    protected fun unparsed(sample: String) {
        metrics.count("$id.unparsed")
        metrics.onUnparsed?.invoke(id, sample)
    }

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

    /**
     * The player's choice of where this feature's block docks beside the hotbar, for a feature that offers one. Dragging
     * the block sets it: into a dock spot, or out of one to [HudDock.FREE].
     */
    open val placement: KMutableProperty0<HudDock>? get() = null

    /** Whether this feature ever shows a HUD block, which is whether it overrides [hud]. */
    val hasHud = javaClass.getMethod("hud", Long::class.javaPrimitiveType).declaringClass != Feature::class.java

    /** A HUD block headed by [title], or with no header when [headed] is false, for rows that speak for themselves. */
    protected fun block(
        headed: Boolean = true,
        look: HudLook = HudLook.PLATE,
        dock: HudDock = HudDock.FREE,
        overMenus: Boolean = true,
        columns: Boolean = false,
        opacity: Float = 1f,
        pops: Boolean = false,
        rows: HudBuilder.() -> Unit,
    ) = HudBlock(if (headed) header else text {}, HudBuilder().apply(rows).rows, look, dock, overMenus, columns, opacity, pops)

    /**
     * The marks on the open container, by slot index, or none. Called when [hud] is, so return the same
     * map until the marks change. A mark on a slot the open container lacks is not drawn.
     */
    open fun slots(now: Long): Map<Int, SlotMark> = emptyMap()

    /** The marks this feature draws in the world, or none. Called when [hud] is, so return equal marks until they change. */
    open fun marks(now: Long): List<Mark> = emptyList()
}
