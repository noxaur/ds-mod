package io.github.noxaur.dragen.core.feature.combat.bosses.bossbeacon

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area
import io.github.noxaur.dragen.core.text.Role
import io.github.noxaur.dragen.core.world.EntityBeam
import io.github.noxaur.dragen.core.feature.combat.bosses.BossMarks
import io.github.noxaur.dragen.core.feature.combat.bosses.SlayerClaim

class BossBeacon(config: DragenConfig, claim: SlayerClaim) : HasFeature {
    override val feature: Feature = Feature(
        config, "boss-beacon", Area.ANYWHERE, Store.SLAYER, "Show a beam over each boss you pick, with its name and distance.", default = false,
        marks = { bosses.marks() },
    )
    val bosses = BossMarks(feature, claim, "beacon", "Mark") { EntityBeam(it.id, it.label, Role.BAD) }
}
