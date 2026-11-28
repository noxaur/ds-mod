package io.github.noxaur.dragen.api.boss.dragon

import io.github.noxaur.dragen.api.Clock
import io.github.noxaur.dragen.api.Ticks
import io.github.noxaur.dragen.api.Ticks.Companion.ticks
import io.github.noxaur.dragen.api.Vec
import io.github.noxaur.dragen.api.boss.Hazard
import io.github.noxaur.dragen.api.boss.HazardKind

internal sealed interface Signal {
    data class Line(val text: String) : Signal

    data class ActionBar(val text: String) : Signal

    data class Moved(val body: Body, val movement: Movement) : Signal

    data class Thrown(val entity: String, val at: Vec, val velocity: Vec) : Signal

    data class Lightning(val at: Vec) : Signal
}

internal interface TraitScope {
    val clock: Clock
    val dragon: Dragon

    fun begin(kind: AttackKind, lasts: Ticks?, beats: List<Beat>)

    fun end()

    fun hazard(hazard: Hazard)

    fun landed(hazard: Hazard)

    fun debuff(kind: DebuffKind, lasts: Ticks?)
}

internal fun interface Trait {
    fun read(signal: Signal, scope: TraitScope)
}

internal class Callout(private val kind: AttackKind, private val callout: Regex, private val lasts: Ticks?, private val beats: List<Beat>) : Trait {
    override fun read(signal: Signal, scope: TraitScope) {
        if (signal is Signal.Line && callout.containsMatchIn(signal.text)) scope.begin(kind, lasts, beats)
    }
}

internal class LineDebuff(private val kind: DebuffKind, private val line: Regex, private val lasts: Ticks?) : Trait {
    override fun read(signal: Signal, scope: TraitScope) {
        if (signal is Signal.Line && line.containsMatchIn(signal.text)) scope.debuff(kind, lasts)
    }
}

internal class Throws(private val kind: HazardKind, private val entity: String) : Trait {
    override fun read(signal: Signal, scope: TraitScope) {
        if (signal is Signal.Thrown && signal.entity == entity) scope.hazard(Hazard.Projectile(kind, signal.at, signal.velocity, scope.clock.now))
    }
}

internal class DragonKit(val traits: List<() -> Trait> = emptyList()) {
    operator fun plus(other: DragonKit) = DragonKit(traits + other.traits)
}

internal object Kits {
    // @keep In `protector-dragon` the dragon hovers 60 ticks, lightning hits it at +20 and +40, then one bolt lands on each player at +60
    private val lightning = Callout(
        DragonAttacks.LIGHTNING, Regex("""I call upon the god of thunder"""), 60.ticks,
        listOf(Beat(20.ticks, DragonHazards.LIGHTNING_WIND_UP), Beat(40.ticks, DragonHazards.LIGHTNING_WIND_UP), Beat(60.ticks, DragonHazards.LIGHTNING)),
    )

    // @keep In `protector-dragon` the dragon hovers about 160 ticks and throws a fireball every 20 from +80
    private val fireball = Callout(DragonAttacks.FIREBALL, Regex("""FIREBALL!!$"""), 160.ticks, (80..160 step 20).map { Beat(it.ticks, DragonHazards.FIREBALL) })

    // @keep Fireballs carry no owner on the client, so the kit takes each one in the End as the dragon's
    private val fireballs = Throws(DragonHazards.FIREBALL, "minecraft:fireball")

    // @keep In 17 of 17 callouts across 28 fights the dragon kept flying at about 1.2 blocks a tick and circled about 34 blocks out, with no stop
    private val circling = Callout(DragonAttacks.CIRCLING, Regex("""You will never catch me!"""), null, emptyList())

    val COMMON = DragonKit(listOf({ lightning }, { fireball }, { fireballs }, { circling }))

    val GOLDEN = COMMON + DragonKit()

    fun of(type: DragonType) = if (type == DragonType.GOLDEN) GOLDEN else COMMON
}
