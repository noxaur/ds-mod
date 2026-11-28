package io.github.noxaur.dragen.api.boss.dragon

import io.github.noxaur.dragen.api.Box
import io.github.noxaur.dragen.api.Clock
import io.github.noxaur.dragen.api.Known
import io.github.noxaur.dragen.api.Progress
import io.github.noxaur.dragen.api.Tick
import io.github.noxaur.dragen.api.Ticks
import io.github.noxaur.dragen.api.Vec
import io.github.noxaur.dragen.api.boss.Hazard
import io.github.noxaur.dragen.api.boss.HazardKind

@JvmInline
public value class DragonId(public val value: Long)

@JvmInline
public value class DragonType(public val name: String) {
    public companion object {
        public val GOLDEN: DragonType = DragonType("Golden")
    }
}

public data class Health(public val current: Long, public val max: Long)

public data class Part(public val name: String, public val box: Box)

public data class Body(public val position: Vec, public val velocity: Vec, public val facing: Vec, public val parts: List<Part>)

public sealed interface Movement {
    public data object Flying : Movement

    public data class Hovering(public val since: Tick) : Movement

    public data class Recovering(public val since: Tick) : Movement

    public data class Diving(public val since: Tick) : Movement
}

@JvmInline
public value class AttackKind(public val name: String)

public data class Beat(public val after: Ticks, public val hazard: HazardKind)

public data class Attack(public val kind: AttackKind, public val since: Tick, public val ends: Known<Tick>, public val beats: List<Beat>) {
    public fun next(hazard: HazardKind, after: Tick): Tick? =
        beats.firstOrNull { it.hazard == hazard && since + it.after > after }?.let { since + it.after }
}

@JvmInline
public value class DebuffKind(public val name: String)

public data class Debuff(public val kind: DebuffKind, public val since: Tick, public val ends: Known<Tick>)

public object DragonAttacks {
    public val LIGHTNING: AttackKind = AttackKind("Lightning")
    public val FIREBALL: AttackKind = AttackKind("Fireball")
    public val CIRCLING: AttackKind = AttackKind("Circling")
}

public object DragonHazards {
    public val LIGHTNING_WIND_UP: HazardKind = HazardKind("Lightning Wind-Up")
    public val LIGHTNING: HazardKind = HazardKind("Lightning")
    public val FIREBALL: HazardKind = HazardKind("Fireball")
}

public interface Dragon {
    public val id: DragonId
    public val type: Known<DragonType>
    public val health: Health
    public val body: Body
    public val movement: Movement
    public val attack: Attack?
    public val hazards: List<Hazard>
    public val debuffs: List<Debuff>

    public fun predict(ahead: Ticks): Vec
}

public sealed interface Cycle {
    public data object Unknown : Cycle

    public data class Summoning(public val eyes: Int, public val needed: Int) : Cycle

    public data class Arriving(public val spawns: Known<Tick>, public val tower: Known<Progress>) : Cycle

    public data class Fighting(public val dragons: List<Dragon>) : Cycle

    public data class Respawning(public val egg: Known<Tick>, public val rebuilt: Known<Progress>, public val deathSite: Known<Vec>) : Cycle
}

public sealed interface DragonEvent {
    public val tick: Tick

    public data class EyePlaced(override val tick: Tick, public val eyes: Int, public val needed: Int) : DragonEvent

    public data class EyeRemoved(override val tick: Tick, public val eyes: Int, public val needed: Int) : DragonEvent

    public data class Summoned(override val tick: Tick, public val spawns: Known<Tick>) : DragonEvent

    public data class Spawned(override val tick: Tick, public val dragon: DragonId, public val type: Known<DragonType>) : DragonEvent

    public data class AttackStarted(override val tick: Tick, public val dragon: DragonId, public val attack: Attack) : DragonEvent

    public data class BeatReached(override val tick: Tick, public val dragon: DragonId, public val attack: AttackKind, public val beat: Beat) : DragonEvent

    public data class AttackEnded(override val tick: Tick, public val dragon: DragonId, public val attack: AttackKind) : DragonEvent

    public data class HazardIncoming(override val tick: Tick, public val dragon: DragonId, public val hazard: Hazard) : DragonEvent

    public data class HazardLanded(override val tick: Tick, public val dragon: DragonId, public val hazard: Hazard) : DragonEvent

    public data class DebuffApplied(override val tick: Tick, public val dragon: DragonId, public val debuff: Debuff) : DragonEvent

    public data class DebuffEnded(override val tick: Tick, public val dragon: DragonId, public val debuff: DebuffKind) : DragonEvent

    public data class DiveStarted(override val tick: Tick, public val dragon: DragonId, public val from: Vec) : DragonEvent

    public data class DiveEnded(override val tick: Tick, public val dragon: DragonId, public val bottom: Vec, public val purge: Purge?) : DragonEvent

    public data class Despawned(override val tick: Tick, public val dragon: DragonId, public val type: Known<DragonType>, public val deathSite: Vec) : DragonEvent

    public data class EggRespawning(override val tick: Tick, public val egg: Known<Tick>) : DragonEvent

    public data class EggSpawned(override val tick: Tick) : DragonEvent
}

public data class Diagnostics(public val counts: Map<String, Int>)

public interface DragonApi {
    public val clock: Clock
    public val cycle: Cycle
    public val diagnostics: Diagnostics

    public fun find(id: DragonId): Dragon?

    public fun watch(): AutoCloseable

    public fun <E : DragonEvent> on(type: Class<E>, handler: (E) -> Unit): AutoCloseable
}

public inline fun <reified E : DragonEvent> DragonApi.on(noinline handler: (E) -> Unit): AutoCloseable = on(E::class.java, handler)
