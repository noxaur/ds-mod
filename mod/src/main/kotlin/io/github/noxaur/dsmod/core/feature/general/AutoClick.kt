package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.config.KeyBind
import io.github.noxaur.dsmod.core.feature.Feature
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
        return walking && earned >= 1 && if (hold) active && down else toggled
    }

    fun spend(nanos: Long): Boolean {
        refill(nanos)
        if (earned < 1) return false
        earned -= 1
        return true
    }

    companion object {
        const val CPS = 9.9
        private const val FULL = 1 + CPS / 20

        fun pair(left: AutoClick, right: AutoClick) {
            left.paired = right
            right.paired = left
        }
    }
}
