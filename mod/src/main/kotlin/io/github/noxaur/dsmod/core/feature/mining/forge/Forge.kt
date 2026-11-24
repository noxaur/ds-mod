package io.github.noxaur.dsmod.core.feature.mining.forge

import io.github.noxaur.dsmod.core.config.Groups
import io.github.noxaur.dsmod.core.feature.mining.forge.forgecraft.ForgeCraft

internal fun Groups.forge() = group("Forge") {
    feature(ForgeCraft(c))
}
