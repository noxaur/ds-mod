package io.github.noxaur.dsmod.core.feature.boss

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.world.Tracer

class BossTracer(config: DsModConfig, claim: SlayerClaim) : HasFeature {
    override val feature: Feature = Feature(
        config, "boss-tracer", Area.ANYWHERE, Store.SLAYER, "Draw a line from your crosshair to each boss you pick.", default = false,
        marks = { bosses.marks() },
    )
    val bosses = BossMarks(feature, claim, "tracer", "Trace") { Tracer(it.id, Role.BAD) }
}
