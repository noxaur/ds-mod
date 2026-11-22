package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.ActionBar
import io.github.noxaur.dsmod.core.game.CombatXp
import io.github.noxaur.dsmod.core.game.EntityDied

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
