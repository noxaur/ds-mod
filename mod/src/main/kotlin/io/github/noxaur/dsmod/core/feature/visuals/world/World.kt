package io.github.noxaur.dsmod.core.feature.visuals.world

import io.github.noxaur.dsmod.core.config.Groups
import io.github.noxaur.dsmod.core.feature.visuals.world.fullbright.Fullbright
import io.github.noxaur.dsmod.core.feature.visuals.world.hideexplosions.HideExplosions

internal fun Groups.world() = group("World") {
    feature(Fullbright(c))
    feature(HideExplosions(c))
}
