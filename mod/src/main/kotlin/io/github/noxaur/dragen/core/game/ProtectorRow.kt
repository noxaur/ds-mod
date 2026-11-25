package io.github.noxaur.dragen.core.game

import io.github.noxaur.dragen.core.text.count

enum class ProtectorStage(val label: String) { RESTING("Resting"), RISING("Rising"), SPAWNED("Spawned") }

// @keep The row reads like `Rising (Tier 2/5, 149)`, `Resting (200)` or `Spawned`
data class ProtectorRow(val stage: ProtectorStage, val tier: String, val killsLeft: Int?) {
    companion object {
        const val CYCLE_KILLS = 200

        private val row = Regex("""^(${ProtectorStage.entries.joinToString("|") { it.label }})(?: \((?:Tier (\d+/\d+), )?(\d+)\))?$""")

        fun parse(value: String): ProtectorRow? {
            val (stage, tier, left) = row.find(value)?.destructured ?: return null
            return ProtectorRow(ProtectorStage.entries.first { it.label == stage }, tier, count(left))
        }
    }
}
