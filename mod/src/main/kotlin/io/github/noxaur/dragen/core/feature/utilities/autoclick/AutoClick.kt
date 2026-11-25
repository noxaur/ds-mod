package io.github.noxaur.dragen.core.feature.utilities.autoclick

import io.github.noxaur.dragen.core.camera.KeyMode
import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.KeyBind
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.config.Switch
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area

class AutoClick(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "auto-click", Area.ANYWHERE, Store.GENERAL, "Click 9.9 times a second while you hold or toggle a key.", default = false, name = "Auto Clicker")

    val left = Part("Left", "Right", MOUSE_LEFT)
    val right = Part("Right", "Left", MOUSE_RIGHT)

    init {
        // @keep Versions up to 0.1.0-beta.16 had Auto Left Click and Auto Right Click, each with its own switch.
        if (listOf("autoLeftClick", "autoRightClick").map { feature.former<Boolean>(it) }.contains(true)) feature.enabled = true
    }

    inner class Part(side: String, otherSide: String, default: Int) {
        private val other get() = if (this === left) right else left

        val key: KeyBind = feature.keys(
            "autoClick${side}Key", "Key", "Pick a ${side.lowercase()} click key that $otherSide Click doesn't use.", default, formerly = listOf("auto${side}ClickKey"),
        ) { it != KeyBind.NONE && it == other.key.value }
        val mode = feature.choice("autoClick${side}Mode", "Mode", "Pick Hold to ${side.lowercase()} click while the key is down, or Toggle.", KeyMode.HOLD, formerly = listOf("auto${side}ClickMode"))
        val switch: Switch = feature.switch(
            "autoClick$side", "$side Click", "$side click on a key while you walk.", default = true,
            formerly = listOf("auto${side}Click"), under = listOf(key, mode),
        )

        val runs: Boolean get() = feature.runs && switch.value

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
    }

    companion object {
        private const val MOUSE_LEFT = 0
        private const val MOUSE_RIGHT = 1
    }
}
