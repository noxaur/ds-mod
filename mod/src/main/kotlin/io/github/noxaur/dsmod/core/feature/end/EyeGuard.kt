package io.github.noxaur.dsmod.core.feature.end

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

/**
 * Keeps a second click from taking back the eye the player just placed. It reads nothing from the server, so
 * all of it is `EyeGuards` in the edge.
 */
class EyeGuard(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "eye-guard", Area.END, Store.END, "Ignore a quick second click that would take a placed eye back.", formerly = listOf("protectPlacedEyes"))
}
