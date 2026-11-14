package io.github.noxaur.dsmod.core.feature.end

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.ActionBar
import io.github.noxaur.dsmod.core.game.Drop
import io.github.noxaur.dsmod.core.game.EntityDied
import io.github.noxaur.dsmod.core.game.SystemMessage
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
 * The server names no killer, but it sends the action bar with the Combat XP gained the moment the player's own kill
 * lands, within 50 ms of the mob's death event in a capture. So a zealot's death followed that closely by such an
 * action bar counts as a kill. In a capture of 180 kills in a crowd this came within a tenth of what the purse gained.
 * A Zealot Bruiser is another mob and is left out. Eyes per hour divide by the time the player has spent in The End, which a stretch
 * in the hub does not dilute, and show only after a minute there, when one eye would give an absurd rate.
 */
class ZealotTracker(toggle: KMutableProperty0<Boolean>) : Feature("zealot-tracker", toggle) {
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
            val since = endSince
            if (tab.area == END && since == null) endSince = now
            if (tab.area != END && since != null) {
                endMillis += now - since
                endSince = null
            }
        }
        on<WorldChanged> { _, now ->
            endSince?.let { endMillis += now - it }
            endSince = null
        }
        on<EntityDied> { death, now ->
            val name = death.name.plain
            if (tab.area != END || "Zealot" !in name || "Bruiser" in name) return@on
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
        on<SystemMessage> { message, _ ->
            // A drop in another area is another tracker's, such as a slayer's Zombie Talisman.
            if (tab.area != END) return@on
            val name = Drop.parse(message.text.plain)?.item ?: return@on
            session += name to (session[name] ?: 0) + 1
            allTime.value = allTime.value.copy(drops = allTime.value.drops.let { it + (name to (it[name] ?: 0) + 1) })
        }
    }

    override fun hud(now: Long): HudBlock? {
        if (tab.area != END) return null
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
        const val END = "The End"
        const val EYE = "Summoning Eye"
        const val MIN_RATE_MILLIS = 60_000L
        const val KILL_WINDOW_MILLIS = 60L
        const val MILLIS_PER_HOUR = 3_600_000.0
    }
}
