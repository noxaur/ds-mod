package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area

/**
 * Looks around in third person on a key without turning the player. It reads nothing from the server and sends
 * nothing, so all of it is `Cameras` in the edge, with the rules in `core.camera`.
 */
class FreeLook(config: DsModConfig) :
    Feature(config, "free-look", Area.ANYWHERE, Store.GENERAL, "Look around in third person while you keep facing ahead.") {
    /** A key's GLFW code, a mouse button from 0 to 9, or -1 for none, as `KeyBind` holds it. */
    val key = keys("freeLookKey", "Key", "Pick the key or mouse button for Free Look.", LEFT_ALT)
    val mode = choice("freeLookMode", "Mode", "Pick Hold to look while the key is down, or Toggle.", KeyMode.HOLD)

    private companion object {
        /** GLFW's code for the left Alt key. */
        const val LEFT_ALT = 342
    }
}
