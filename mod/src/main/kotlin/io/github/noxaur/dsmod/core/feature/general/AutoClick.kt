package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.KeyBind
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.config.Switch
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import kotlin.math.ceil
import kotlin.math.min

class AutoClick(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "auto-click", Area.ANYWHERE, Store.GENERAL, "Click 9.9 times a second on a key while you walk.", default = false)

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
    }

    companion object {
        const val CPS = 9.9
        private const val FULL = 1 + CPS / 20
        private const val SECOND = 1_000_000_000L
        private const val MOUSE_LEFT = 0
        private const val MOUSE_RIGHT = 1
    }
}
