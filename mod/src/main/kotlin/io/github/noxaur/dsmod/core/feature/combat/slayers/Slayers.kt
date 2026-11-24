package io.github.noxaur.dsmod.core.feature.combat.slayers

import io.github.noxaur.dsmod.core.config.Groups
import io.github.noxaur.dsmod.core.feature.combat.slayers.slayerbossalert.SlayerBossAlert
import io.github.noxaur.dsmod.core.feature.combat.slayers.slayertracker.SlayerTracker

internal fun Groups.slayers() = group("Slayers") {
    feature(SlayerTracker(c))
    feature(SlayerBossAlert(c))
}
