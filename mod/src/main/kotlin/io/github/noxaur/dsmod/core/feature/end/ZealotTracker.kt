package io.github.noxaur.dsmod.core.feature.end

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.hud.HudBlock
import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.reflect.KMutableProperty0

/** Every drop the player has had, by the name the server gives it. */
@Serializable
data class ZealotDrops(val drops: Map<String, Int> = emptyMap())

/**
 * Counts the player's own drop lines, such as `RARE DROP!`, `EPIC DROP!` and `RNGESUS DROP!`. The server sends no per-kill signal, so
 * kills are not counted. Eyes per hour divide by the time the player has spent in The End, which a stretch
 * in the hub does not dilute, and show only after a minute there, when one eye would give an absurd rate.
 */
class ZealotTracker(toggle: KMutableProperty0<Boolean>) : Feature("zealot-tracker", toggle) {
    private val allTime = saved(ZealotDrops())
    private var session = emptyMap<String, Int>()
    private var endMillis = 0L
    private var endSince: Long? = null

    // A player's chat line has their name first, so only the server's own lines start with the drop.
    private val drop = Regex("""^[A-Z]+ DROP! (?:\((.+?)\)(?: \(.*\))?|(.+))$""")

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
        on<SystemMessage> { message, _ ->
            // A drop in another area is another tracker's, such as a slayer's Zombie Talisman.
            if (tab.area != END) return@on
            val line = message.text.plain
            if (" DROP! " !in line) return@on
            val (parenthesised, bare) = drop.find(line)?.destructured ?: return@on
            val name = parenthesised.ifEmpty { bare }
            session += name to (session[name] ?: 0) + 1
            allTime.value = ZealotDrops(allTime.value.drops.let { it + (name to (it[name] ?: 0) + 1) })
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
        const val MILLIS_PER_HOUR = 3_600_000.0
    }
}
