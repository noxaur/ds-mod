package io.github.noxaur.dragen.core.feature.mining.forge

import io.github.noxaur.dragen.core.config.Groups
import io.github.noxaur.dragen.core.feature.mining.forge.forgecraft.ForgeCraft

internal fun Groups.forge() = group("Forge") {
    feature(ForgeCraft(c))
}
