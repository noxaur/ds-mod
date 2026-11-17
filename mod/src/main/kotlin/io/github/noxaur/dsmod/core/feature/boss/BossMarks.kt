package io.github.noxaur.dsmod.core.feature.boss

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.Boss
import io.github.noxaur.dsmod.core.game.BossKind
import io.github.noxaur.dsmod.core.game.EntitySeen
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.world.Mark

/**
 * A mark on each boss the client holds that the player picks, in red, as `Boss.parse` reads each entity. The server
 * names no slayer boss's owner, and tells only the owner `☠ SLAYER BOSS SPAWNING!`, so the first slayer boss to come
 * into view within [OWN_MILLIS] of that line is the player's. In `slayer-boss` it came 1.5s after the line.
 *
 * [key] starts each pick's key, as `beaconDragons`, and [verb] each pick's description, as `Mark the End's dragons.`
 */
abstract class BossMarks(config: DsModConfig, id: String, description: String, key: String, verb: String) :
    Feature(config, id, Area.ANYWHERE, Store.SLAYER, description, default = false) {
    val dragons = switch("${key}Dragons", "Dragons", "$verb the End's dragons.", default = true)
    val protector = switch("${key}Protector", "Endstone Protector", "$verb the Endstone Protector.", default = true)
    val yourSlayer = switch("${key}YourSlayer", "Your Slayer Boss", "$verb the slayer boss you spawned.", default = true)
    val otherSlayers = switch("${key}OtherSlayers", "Other Slayer Bosses", "$verb slayer bosses other players spawned.", default = false)
    private var spawnedAt: Long? = null
    private var own: Int? = null

    init {
        readEntities(*Boss.FILTERS.toTypedArray())
        on<WorldChanged> { _, _ -> own = null }
        line("☠ SLAYER BOSS SPAWNING!") { now -> spawnedAt = now }
        on<EntitySeen> { seen, now ->
            if (entities.any { it.id == seen.id }) return@on
            val boss = Boss.parse(seen) ?: return@on unparsed(seen.name.plain)
            if (boss.kind != BossKind.SLAYER || spawnedAt?.let { now - it <= OWN_MILLIS } != true) return@on
            own = boss.id
            spawnedAt = null
        }
    }

    /** The mark this feature draws on [boss]. */
    protected abstract fun mark(boss: Boss): Mark

    override fun marks(now: Long) = entities.mapNotNull(Boss::parse).filter { boss ->
        when (boss.kind) {
            BossKind.DRAGON -> dragons.value
            BossKind.PROTECTOR -> protector.value
            BossKind.SLAYER -> if (boss.id == own) yourSlayer.value else otherSlayers.value
        }
    }.map(::mark)

    private companion object {
        const val OWN_MILLIS = 5_000L
    }
}
