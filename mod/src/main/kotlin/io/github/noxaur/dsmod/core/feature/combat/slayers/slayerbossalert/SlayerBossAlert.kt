package io.github.noxaur.dsmod.core.feature.combat.slayers.slayerbossalert

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.SlayerLine
import io.github.noxaur.dsmod.core.text.text

class SlayerBossAlert(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "slayer-boss-alert", Area.ANYWHERE, Store.SLAYER, "Alert you with a title and sound when your slayer boss spawns.")

    init {
        feature.line(SlayerLine.SPAWNED) { feature.alert(text { warning("Boss Spawned") }, text { body("Your slayer boss spawned.") }) }
    }
}
