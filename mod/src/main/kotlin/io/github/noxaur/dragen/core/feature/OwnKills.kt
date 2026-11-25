package io.github.noxaur.dragen.core.feature

import io.github.noxaur.dragen.core.game.ActionBar
import io.github.noxaur.dragen.core.game.EntityDied
import io.github.noxaur.dragen.core.text.number

class OwnKills(feature: Feature, accept: (EntityDied) -> Boolean, kill: (count: Int, xp: Double?, now: Long) -> Unit) {
    private val died = LongArray(8)
    private var diedNext = 0

    init {
        feature.on<EntityDied> { death, now ->
            if (!accept(death)) return@on
            died[diedNext] = now
            diedNext = (diedNext + 1) % died.size
        }
        feature.on<ActionBar> { bar, now ->
            val text = bar.text.plain
            if (CombatXp.MARK !in text) return@on
            var count = 0
            for (i in died.indices) {
                if (died[i] == 0L || now - died[i] !in 0..CombatXp.KILL_WINDOW_MILLIS) continue
                died[i] = 0
                count++
            }
            if (count == 0) return@on
            val xp = CombatXp.gained(text)
            if (xp == null) feature.unparsed(text)
            kill(count, xp, now)
        }
    }
}

/** @keep The server sends it right behind the death of a mob the player killed, within KILL_WINDOW_MILLIS, and
 * repeats it for about a second after */
private object CombatXp {
    const val MARK = " Combat ("
    const val KILL_WINDOW_MILLIS = 20L
    private val GAIN = Regex("""\+([\d,.]+) Combat \(""")

    fun gained(plain: String): Double? = GAIN.find(plain)?.groupValues?.get(1)?.let(::number)
}
