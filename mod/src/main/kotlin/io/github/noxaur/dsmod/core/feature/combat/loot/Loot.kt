package io.github.noxaur.dsmod.core.feature.combat.loot

import io.github.noxaur.dsmod.core.config.Groups
import io.github.noxaur.dsmod.core.feature.combat.loot.raredropalert.RareDropAlert

internal fun Groups.loot() = group("Loot") {
    feature(RareDropAlert(c))
}
