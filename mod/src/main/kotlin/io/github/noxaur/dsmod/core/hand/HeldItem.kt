package io.github.noxaur.dsmod.core.hand

import kotlin.math.exp
import kotlin.math.roundToInt

/** How the held item moves when the player swings. */
enum class SwingStyle(private val label: String) {
    VANILLA("Vanilla"),

    /** The old block-hit: the sword held across the screen as it swings. */
    BLOCK_HIT("1.7"),

    /** One full turn per swing. */
    SPIN("Spin"),

    /** Straight out at the crosshair and back, as the game swings a spear. */
    STAB("Stab"),

    /** No movement at all. */
    STILL("Still"),
    ;

    override fun toString() = label
}

object HeldItem {
    /**
     * How many ticks a swing of [ticks] lasts at [speed], where 1 is the game's own. Never under one tick, or the
     * swing would end before it is drawn.
     */
    fun swingTicks(ticks: Int, speed: Float): Int = if (speed == 1f) ticks else (ticks / speed).roundToInt().coerceAtLeast(1)
}

/**
 * Eases a pose in and out over about a tenth of a second, so the block-hit pose follows whether the player is
 * swinging and not each swing, which a spam-click restarts every few ticks.
 */
class Ease {
    var shown = 0f
        private set
    private var last = 0L

    /** The amount to draw at [nanos], on the way to [target]. */
    fun at(target: Float, nanos: Long): Float {
        val seconds = if (last == 0L) 0f else ((nanos - last) / 1e9f).coerceIn(0f, 0.1f)
        last = nanos
        shown += (target - shown) * (1 - exp(-seconds / SECONDS))
        if (target == 0f && shown < 0.002f) shown = 0f
        return shown
    }

    private companion object {
        /** Two thirds of the way in each 40 ms. */
        const val SECONDS = 0.04f
    }
}
