package io.github.noxaur.dsmod.core.feature.boss

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.world.EntityBeam

class BossBeacon(config: DsModConfig, claim: SlayerClaim) : HasFeature {
    override val feature: Feature = Feature(
        config, "boss-beacon", Area.ANYWHERE, Store.SLAYER, "Show a beam over each boss you pick, with its distance.", default = false,
        marks = { bosses.marks() },
    )
    val bosses = BossMarks(feature, claim, "beacon", "Mark") { EntityBeam(it.id, it.label, Role.BAD) }
}
