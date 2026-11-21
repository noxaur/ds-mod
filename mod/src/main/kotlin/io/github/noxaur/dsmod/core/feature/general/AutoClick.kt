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
    private var earned = 1.0

    fun tick(on: Boolean, walking: Boolean, pressed: Boolean, down: Boolean): Boolean {
        val active = on && key.value != KeyBind.NONE
        val hold = mode.value == KeyMode.HOLD
        val press = pressed && !wasDown
        wasDown = down
        toggled = active && !hold && toggled != press
        earned = min(earned + PER_TICK, 1 + PER_TICK)
        return walking && earned >= 1 && if (hold) active && down else toggled
    }

    fun spend(): Boolean {
        if (earned < 1) return false
        earned -= 1
        return true
    }

    companion object {
        const val CPS = 9.9
        private const val PER_TICK = CPS / 20

        fun pair(left: AutoClick, right: AutoClick) {
            left.paired = right
            right.paired = left
        }
    }
}
