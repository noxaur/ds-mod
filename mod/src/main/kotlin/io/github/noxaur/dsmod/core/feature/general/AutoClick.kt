package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.config.KeyBind
import io.github.noxaur.dsmod.core.feature.Feature

class AutoClick(feature: Feature, id: String, button: String, other: String, default: Int) {
    lateinit var paired: AutoClick

    val key: KeyBind = feature.keys("${id}Key", "Key", "Pick a $button click key that $other doesn't use.", default) {
        it != KeyBind.NONE && it == paired.key.value
    }
    val mode = feature.choice("${id}Mode", "Mode", "Pick Hold to $button click while the key is down, or Toggle.", KeyMode.HOLD)

    private var wasDown = false
    private var toggled by feature.world { false }

    fun tick(on: Boolean, walking: Boolean, pressed: Boolean, down: Boolean): Boolean {
        val active = on && key.value != KeyBind.NONE
        val hold = mode.value == KeyMode.HOLD
        val press = pressed && !wasDown
        wasDown = down
        toggled = active && !hold && toggled != press
        return walking && if (hold) active && down else toggled
    }

    companion object {
        fun pair(left: AutoClick, right: AutoClick) {
            left.paired = right
            right.paired = left
        }
    }
}
