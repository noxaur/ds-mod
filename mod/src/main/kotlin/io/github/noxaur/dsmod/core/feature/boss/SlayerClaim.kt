package io.github.noxaur.dsmod.core.feature.boss

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Boss
import io.github.noxaur.dsmod.core.game.BossKind
import io.github.noxaur.dsmod.core.game.EntitySeen
import io.github.noxaur.dsmod.core.game.SlayerLine

/** @keep The server names no slayer boss's owner and tells only the owner `☠ SLAYER BOSS SPAWNING!`, so the first
 * slayer boss in view within OWN_MILLIS of that line is the player's. In `slayer-boss` it came 1.5s after the
 * line */
class SlayerClaim {
    private var world = -1
    private var spawnedAt: Long? = null
    private var own: Int? = null

    private val considered = HashSet<Int>()

    fun owns(feature: Feature, boss: Boss) = boss.id == current(feature).own

    fun follow(feature: Feature) {
        feature.line(SlayerLine.SPAWNED) { now -> current(feature).spawnedAt = now }
        feature.on<EntitySeen> { seen, now ->
            if (feature.entities.any { it.id == seen.id }) return@on
            val boss = Boss.parse(seen) ?: return@on feature.unparsed(seen.name.plain)
            if (boss.kind != BossKind.SLAYER || !current(feature).considered.add(boss.id)) return@on
            if (spawnedAt?.let { now - it <= OWN_MILLIS } != true) return@on
            own = boss.id
            spawnedAt = null
        }
    }

    private fun current(feature: Feature): SlayerClaim {
        if (world != feature.worldNumber) {
            world = feature.worldNumber
            spawnedAt = null
            own = null
            considered.clear()
        }
        return this
    }

    private companion object {
        const val OWN_MILLIS = 5_000L
    }
}
