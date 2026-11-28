package io.github.noxaur.dragen.api.boss.dragon

import io.github.noxaur.dragen.api.Vec
import io.github.noxaur.dragen.api.world.Zone
import io.github.noxaur.dragen.api.world.Zones

/** @keep A purge is named for what a player sees it dive at. The gate purge lands on the den's spawn, beside the gate, so
 * calling it a spawn purge read as the dragon diving at where it spawned. */
public enum class Purge(public val zone: Zone) {
    ALTAR(Zones.DRAGON_ALTAR),
    GATE(Zones.DRAGONS_DEN_SPAWN),
    ;

    internal companion object {
        /** @keep Across 28 fights 15 of 16 dives bottomed at most 10 blocks across from one of these zones and no higher than
         * its top. The 16th turned back 11 blocks above the altar. */
        const val REACH = 12.0

        fun of(bottom: Vec): Purge? = entries.filter { bottom.y < it.zone.area.maxY + 1 && it.zone.area.across(bottom) <= REACH }
            .minByOrNull { it.zone.area.across(bottom) }
    }
}
