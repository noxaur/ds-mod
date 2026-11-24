package io.github.noxaur.dsmod.core.feature.general.world

import io.github.noxaur.dsmod.core.config.Groups
import io.github.noxaur.dsmod.core.feature.general.world.fullbright.Fullbright
import io.github.noxaur.dsmod.core.feature.general.world.hideexplosions.HideExplosions

internal fun Groups.world() = group("World") {
    feature(Fullbright(c))
    feature(HideExplosions(c))
}
