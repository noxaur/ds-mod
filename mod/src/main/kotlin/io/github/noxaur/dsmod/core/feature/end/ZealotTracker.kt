package io.github.noxaur.dsmod.core.feature.end

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.ActionBar
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.Drop
import io.github.noxaur.dsmod.core.game.EntityDied
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.hud.HudBlock
import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.reflect.KMutableProperty0

/** Every drop the player has had, by the name the server gives it, and the zealots they have killed. */
@Serializable
data class ZealotDrops(val drops: Map<String, Int> = emptyMap(), val kills: Int = 0)

/**
 * Counts the player's own drop lines, such as `RARE DROP!`, `EPIC DROP!` and `RNGESUS DROP!`, and estimates their zealot kills.
 * The server names no killer, but it sends the action bar with the Combat XP gained right behind the death event of
 * the mob the player killed: in a capture of 430 kills in a crowd, all but five came within 5 ms. So a zealot's death
 * followed that closely by such an action bar counts as a kill. The action bar repeats the last gain for about a second,
 * so another player's zealot that dies just ahead of a repeat is counted too, which the short window keeps rare.
 * A Zealot Bruiser is another mob and is left out. Eyes per hour divide by the time the player has spent in The End, which a stretch
 * in the hub does not dilute, and show only after a minute there, when one eye would give an absurd rate.
 */
class ZealotTracker(toggle: KMutableProperty0<Boolean>) : Feature("zealot-tracker", toggle, Area.END) {
    private val allTime = saved(ZealotDrops())
    private var session = emptyMap<String, Int>()
    private var endMillis = 0L
    private var endSince: Long? = null
    private var kills = 0

    // When the last few zealots died. One action bar can confirm several that died together.
    private val died = LongArray(8)
    private var diedNext = 0

    init {
        on<TabList> { _, now ->
            if (here && endSince == null) endSince = now
            if (!here) leaveEnd(now)
        }
        on<WorldChanged> { _, now -> leaveEnd(now) }
        on<EntityDied> { death, now ->
            val name = death.name.plain
            if ("Zealot" !in name || "Bruiser" in name) return@on
            died[diedNext] = now
            diedNext = (diedNext + 1) % died.size
        }
        on<ActionBar> { bar, now ->
            if (" Combat (" !in bar.text.plain) return@on
            var confirmed = 0
            for (i in died.indices) {
                if (died[i] == 0L || now - died[i] !in 0..KILL_WINDOW_MILLIS) continue
                died[i] = 0
                confirmed++
            }
            if (confirmed == 0) return@on
            kills += confirmed
            allTime.value = allTime.value.copy(kills = allTime.value.kills + confirmed)
        }
        line(Drop.head, Drop::parse) { drop, _ ->
            val name = drop.item
            session += name to (session[name] ?: 0) + 1
            allTime.value = allTime.value.copy(drops = allTime.value.drops.let { it + (name to (it[name] ?: 0) + 1) })
        }
    }

    private fun leaveEnd(now: Long) {
        endSince?.let { endMillis += now - it }
        endSince = null
    }

    override fun hud(now: Long): HudBlock? {
        val eyes = session[EYE] ?: 0
        val inEnd = endMillis + (endSince?.let { now - it } ?: 0)
        return block {
            row("Eyes") { value(eyes.toLong()) }
            row("Eyes per Hour") { value(if (inEnd >= MIN_RATE_MILLIS) "%.1f".format(Locale.US, eyes * MILLIS_PER_HOUR / inEnd) else "-") }
            row("All-Time Eyes") { value((allTime.value.drops[EYE] ?: 0).toLong()) }
            if (kills > 0) {
                row("Kills") { value(kills.toLong()); muted(" (${allTime.value.kills} all-time)") }
                if (eyes > 0) row("Kills per Eye") { value("%.1f".format(Locale.US, kills.toDouble() / eyes)) }
            }
            for ((name, count) in session) {
                if (name == EYE) continue
                row(name) {
                    value(count.toLong())
                    muted(" (${allTime.value.drops.getValue(name)} all-time)")
                }
            }
        }
    }

    private companion object {
        const val EYE = "Summoning Eye"
        const val MIN_RATE_MILLIS = 60_000L
        const val KILL_WINDOW_MILLIS = 20L
        const val MILLIS_PER_HOUR = 3_600_000.0
    }
}
