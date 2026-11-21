package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.config.Choice
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.KeyBind
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import kotlin.math.min

class AutoClicker(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "auto-clicker", Area.ANYWHERE, Store.GENERAL, "Click 9.9 times a second on a key while you walk.", default = false)

    val left = Trigger(
        feature.keys("autoClickerLeftKey", "Left Click Key", "Pick the key or mouse button that left clicks while you walk.", MOUSE_LEFT),
        feature.choice("autoClickerLeftMode", "Left Click Mode", "Pick Hold to left click while the key is down, or Toggle.", KeyMode.HOLD),
    )
    val right = Trigger(
        feature.keys("autoClickerRightKey", "Right Click Key", "Pick the key or mouse button that right clicks while you walk.", MOUSE_RIGHT),
        feature.choice("autoClickerRightMode", "Right Click Mode", "Pick Hold to right click while the key is down, or Toggle.", KeyMode.HOLD),
    )

    class Trigger(val key: KeyBind, val mode: Choice<KeyMode>) {
        private var wasDown = false
        private var holding = false
        private var toggled = false
        private var earned = 1.0

        fun tick(on: Boolean, walking: Boolean, pressed: Boolean, down: Boolean): Boolean {
            val active = on && key.value != KeyBind.NONE
            val hold = mode.value == KeyMode.HOLD
            val press = pressed && !wasDown
            wasDown = down
            holding = active && hold && (press || holding && down)
            toggled = active && !hold && toggled != press
            earned = min(earned + PER_TICK, 1 + PER_TICK)
            if (!walking || earned < 1 || !(holding || toggled)) return false
            earned -= 1
            return true
        }
    }

    companion object {
        const val CPS = 9.9
        private const val PER_TICK = CPS / 20
        private const val MOUSE_LEFT = 0
        private const val MOUSE_RIGHT = 1
    }
}
