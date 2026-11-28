package io.github.noxaur.dragen.api.boss

import io.github.noxaur.dragen.api.Known
import io.github.noxaur.dragen.api.Tick
import io.github.noxaur.dragen.api.Vec

@JvmInline
public value class HazardKind(public val name: String)

public sealed interface Hazard {
    public val kind: HazardKind
    public val lands: Known<Tick>

    public data class Strike(override val kind: HazardKind, override val lands: Known<Tick>, public val at: Known<Vec>) : Hazard

    public data class Projectile(override val kind: HazardKind, public val from: Vec, public val velocity: Vec, public val seen: Tick) : Hazard {
        override val lands: Known<Tick> get() = Known.Unknown

        public fun at(tick: Tick): Vec = from + velocity * (tick - seen).count
    }
}
