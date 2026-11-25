package io.github.noxaur.dragen.core.feature.combat.bosses.bosstracer

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area
import io.github.noxaur.dragen.core.text.Role
import io.github.noxaur.dragen.core.world.Tracer
import io.github.noxaur.dragen.core.feature.combat.bosses.BossMarks
import io.github.noxaur.dragen.core.feature.combat.bosses.SlayerClaim

class BossTracer(config: DragenConfig, claim: SlayerClaim) : HasFeature {
    override val feature: Feature = Feature(
        config, "boss-tracer", Area.ANYWHERE, Store.SLAYER, "Draw a line from your crosshair to each boss you pick.", default = false,
        marks = { bosses.marks() },
    )
    val bosses = BossMarks(feature, claim, "tracer", "Trace") { Tracer(it.id, Role.BAD) }
}
