package io.github.noxaur.dsmod.core.feature.boss

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.game.Boss
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.world.EntityBeam

/** A beam over each boss the player picks, named and with the distance to it, which follows the boss. */
class BossBeacon(config: DsModConfig) :
    BossMarks(config, "boss-beacon", "Show a beam over each boss you pick, with its distance.", "beacon", "Mark") {
    override fun mark(boss: Boss) = EntityBeam(boss.id, boss.label, Role.BAD)
}
