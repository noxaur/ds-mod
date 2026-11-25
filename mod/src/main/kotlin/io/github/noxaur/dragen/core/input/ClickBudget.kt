package io.github.noxaur.dragen.core.input

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class ClickBudget {
    private var earned = FULL
    private var refilled = Long.MIN_VALUE
    private val recent = LongArray(ceil(CPS).toInt()) { Long.MIN_VALUE }
    private var oldest = 0

    fun admits(nanos: Long): Boolean {
        refill(nanos)
        return earned >= 1 && recent[oldest].let { it == Long.MIN_VALUE || nanos - it >= SECOND }
    }

    fun spend(nanos: Long): Boolean {
        if (!admits(nanos)) return false
        record(nanos)
        return true
    }

    fun count(nanos: Long) {
        refill(nanos)
        record(nanos)
    }

    private fun refill(nanos: Long) {
        if (refilled != Long.MIN_VALUE) earned = min(earned + (nanos - refilled) * CPS / 1e9, FULL)
        refilled = nanos
    }

    private fun record(nanos: Long) {
        earned = max(earned - 1, -CPS)
        recent[oldest] = nanos
        oldest = (oldest + 1) % recent.size
    }

    companion object {
        private const val CPS = 9.9
        private const val FULL = 1 + CPS / 20
        private const val SECOND = 1_000_000_000L
    }
}
