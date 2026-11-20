package io.github.noxaur.dsmod.core.feature.boss

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Boss
import io.github.noxaur.dsmod.core.game.BossKind
import io.github.noxaur.dsmod.core.game.EntitySeen

/**
 * Which slayer boss is the player's, one claim that every feature marking bosses shares, so they always agree. The
 * server names no slayer boss's owner, and tells only the owner `☠ SLAYER BOSS SPAWNING!`, so the first slayer boss to
 * come into view within [OWN_MILLIS] of that line is the player's. In `slayer-boss` it came 1.5s after the line.
 *
 * Each feature that [follow]s it feeds it while that feature is on, so a feature switched on after the spawn line
 * still knows the boss. `SettingsTree` builds one and hands it to each.
 */
class SlayerClaim {
    /** The [Feature.worldNumber] that [spawnedAt] and [own] belong to. */
    private var world = -1
    private var spawnedAt: Long? = null
    private var own: Int? = null

    /**
     * The slayer bosses already looked at in this world. A feature that comes on hears the bosses the client already
     * holds as if they came then, and one that was in view before the spawn line is not the player's.
     */
    private val considered = HashSet<Int>()

    /** Whether [boss], which [feature] reads, is the player's own slayer boss. */
    fun owns(feature: Feature, boss: Boss) = boss.id == current(feature).own

    /** Feeds the claim from [feature]'s events. [feature] reads the slayer bosses' entities. */
    fun follow(feature: Feature) {
        feature.line("☠ SLAYER BOSS SPAWNING!") { now -> current(feature).spawnedAt = now }
        feature.on<EntitySeen> { seen, now ->
            if (feature.entities.any { it.id == seen.id }) return@on
            val boss = Boss.parse(seen) ?: return@on feature.unparsed(seen.name.plain)
            if (boss.kind != BossKind.SLAYER || !current(feature).considered.add(boss.id)) return@on
            if (spawnedAt?.let { now - it <= OWN_MILLIS } != true) return@on
            own = boss.id
            spawnedAt = null
        }
    }

    /**
     * Forgets a claim from a world that ended. It goes by the world's number, not a feature hearing the world end,
     * because `Game` also tells a feature that goes off, or comes back on after the player left, while the other
     * feature that shares the claim may have stayed on and claimed the new world's boss.
     */
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
