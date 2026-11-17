package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area

/**
 * Lights the whole world. It reads nothing from the server, so all of it is `FullbrightMixin` in the edge,
 * which asks the setting each frame. This class puts it in the settings, the docs check and the tests.
 */
class Fullbright(config: DsModConfig) :
    Feature(config, "fullbright", Area.ANYWHERE, Store.GENERAL, "Light caves and nights as brightly as day.", default = false)
