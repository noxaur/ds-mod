package io.github.noxaur.dragen.api

import kotlin.math.floor

/** @keep dragsim.co can lag without telling the client, whose world then keeps its rate of 20, so the API's rate is measured
 * from how far the server's clock moves between its time packets, by the server clock. */
@JvmInline
public value class TickRate(public val perSecond: Double) {
    init {
        require(perSecond > 0) { "A tick rate is above zero, not $perSecond" }
    }

    public val millisPerTick: Double get() = 1000 / perSecond

    public fun millis(span: Ticks): Double = span.count * millisPerTick

    public fun ticks(millis: Double): Ticks = Ticks(millis / millisPerTick)

    public fun seconds(span: Ticks): Double = span.count / perSecond

    public companion object {
        public val NORMAL: TickRate = TickRate(20.0)
    }
}

@JvmInline
public value class Ticks(public val count: Double) : Comparable<Ticks> {
    public val whole: Long get() = floor(count).toLong()

    public operator fun plus(other: Ticks): Ticks = Ticks(count + other.count)

    public operator fun minus(other: Ticks): Ticks = Ticks(count - other.count)

    public operator fun times(scale: Double): Ticks = Ticks(count * scale)

    override fun compareTo(other: Ticks): Int = count.compareTo(other.count)

    public companion object {
        public val ZERO: Ticks = Ticks(0.0)

        public val Int.ticks: Ticks get() = Ticks(toDouble())

        public val Double.ticks: Ticks get() = Ticks(this)

        public fun Int.seconds(rate: TickRate): Ticks = Ticks(this * rate.perSecond)
    }
}

/** @keep A point on the client world's game time. The client adds one to it each tick while its world runs normally, so it
 * stops while the server freezes ticking, and the server overwrites it with each time packet, so it can step either way
 * then. It starts again in each world, so two ticks from different worlds don't compare. The fraction is the part of a
 * tick a frame has drawn past the last whole one. */
@JvmInline
public value class Tick(public val value: Double) : Comparable<Tick> {
    public val whole: Long get() = floor(value).toLong()

    public val fraction: Double get() = value - whole

    public operator fun plus(span: Ticks): Tick = Tick(value + span.count)

    public operator fun minus(span: Ticks): Tick = Tick(value - span.count)

    public operator fun minus(earlier: Tick): Ticks = Ticks(value - earlier.value)

    override fun compareTo(other: Tick): Int = value.compareTo(other.value)

    public companion object {
        public fun of(gameTime: Long, partial: Double = 0.0): Tick {
            require(partial in 0.0..<1.0) { "A partial tick is in [0, 1), not $partial" }
            return Tick(gameTime + partial)
        }
    }
}

public interface Clock {
    public val now: Tick

    public val rate: TickRate
}
