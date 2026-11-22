package io.github.noxaur.dsmod.core.game

import io.github.noxaur.dsmod.core.text.count

/**
 * The tab list's `Protector` row, such as `Rising (Tier 2/5, 149)`, `Resting (200)` or `Spawned`. [tier] is
 * `2/5` or empty, and [killsLeft] is the zealot kills the lobby still needs before the Protector emerges, or
 * null when the row gives none.
 */
data class ProtectorRow(val stage: String, val tier: String, val killsLeft: Int?) {
    companion object {
        /** The kills one cycle takes, which is what the row reads right after a defeat: `Resting (200)`. */
        const val CYCLE_KILLS = 200

        private val row = Regex("""^(Resting|Rising|Spawned)(?: \((?:Tier (\d+/\d+), )?(\d+)\))?$""")

        /** The row's parts, or null when it has another shape. */
        fun parse(value: String): ProtectorRow? {
            val (stage, tier, left) = row.find(value)?.destructured ?: return null
            return ProtectorRow(stage, tier, count(left))
        }
    }
}
