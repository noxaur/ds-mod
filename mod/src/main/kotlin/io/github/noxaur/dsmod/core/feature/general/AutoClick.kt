package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.config.KeyBind
import io.github.noxaur.dsmod.core.feature.Feature
import kotlin.math.ceil
import kotlin.math.min

class AutoClick(feature: Feature, id: String, button: String, other: String, default: Int) {
    lateinit var paired: AutoClick

    val key: KeyBind = feature.keys("${id}Key", "Key", "Pick a $button click key that $other doesn't use.", default) {
        it != KeyBind.NONE && it == paired.key.value
    }
    val mode = feature.choice("${id}Mode", "Mode", "Pick Hold to $button click while the key is down, or Toggle.", KeyMode.HOLD)

    private var wasDown = false
    private var toggled by feature.world { false }
    private var earned = FULL
    private var refilled: Long? = null
    private val recent = LongArray(ceil(CPS).toInt()) { Long.MIN_VALUE }
    private var oldest = 0

    private fun refill(nanos: Long) {
        refilled?.let { earned = min(earned + (nanos - it) * CPS / 1e9, FULL) }
        refilled = nanos
    }

    fun tick(on: Boolean, walking: Boolean, pressed: Boolean, down: Boolean, nanos: Long): Boolean {
        val active = on && key.value != KeyBind.NONE
        val hold = mode.value == KeyMode.HOLD
        val press = pressed && !wasDown
        wasDown = down
        toggled = active && !hold && toggled != press
        refill(nanos)
        return walking && admits(nanos) && if (hold) active && down else toggled
    }

    private fun admits(nanos: Long) = earned >= 1 && recent[oldest].let { it == Long.MIN_VALUE || nanos - it >= SECOND }

    fun spend(nanos: Long): Boolean {
        refill(nanos)
        if (!admits(nanos)) return false
        earned -= 1
        recent[oldest] = nanos
        oldest = (oldest + 1) % recent.size
        return true
    }

    companion object {
        const val CPS = 9.9
        private const val FULL = 1 + CPS / 20
        private const val SECOND = 1_000_000_000L

        fun pair(left: AutoClick, right: AutoClick) {
            left.paired = right
            right.paired = left
        }
    }
}
