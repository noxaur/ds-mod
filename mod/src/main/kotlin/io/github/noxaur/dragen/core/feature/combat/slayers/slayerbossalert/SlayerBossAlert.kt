package io.github.noxaur.dragen.core.feature.combat.slayers.slayerbossalert

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area
import io.github.noxaur.dragen.core.game.SlayerLine
import io.github.noxaur.dragen.core.text.text

class SlayerBossAlert(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "slayer-boss-alert", Area.ANYWHERE, Store.SLAYER, "Alert you when your slayer boss spawns.")

    init {
        feature.line(SlayerLine.SPAWNED) { feature.alert(text { warning("Boss Spawned") }, text { body("Your slayer boss spawned.") }) }
    }
}
