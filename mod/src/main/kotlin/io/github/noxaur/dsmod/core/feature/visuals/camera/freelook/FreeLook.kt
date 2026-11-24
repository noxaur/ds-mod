package io.github.noxaur.dsmod.core.feature.visuals.camera.freelook

import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

class FreeLook(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "free-look", Area.ANYWHERE, Store.GENERAL, "Look around freely without changing your direction of movement.")

    val key = feature.keys("freeLookKey", "Key", "Pick the key or mouse button for Free Look.", LEFT_ALT)
    val mode = feature.choice("freeLookMode", "Mode", "Pick Hold to look while the key is down, or Toggle.", KeyMode.HOLD)

    private companion object {
        const val LEFT_ALT = 342
    }
}
