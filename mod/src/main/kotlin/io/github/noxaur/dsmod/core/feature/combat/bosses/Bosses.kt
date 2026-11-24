package io.github.noxaur.dsmod.core.feature.combat.bosses

import io.github.noxaur.dsmod.core.config.Groups
import io.github.noxaur.dsmod.core.feature.combat.bosses.bossbeacon.BossBeacon
import io.github.noxaur.dsmod.core.feature.combat.bosses.bosstracer.BossTracer

internal fun Groups.bosses() = group("Bosses") {
    val claim = SlayerClaim()
    feature(BossBeacon(c, claim))
    feature(BossTracer(c, claim))
}
