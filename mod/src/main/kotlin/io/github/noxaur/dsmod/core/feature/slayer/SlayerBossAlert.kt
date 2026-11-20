package io.github.noxaur.dsmod.core.feature.slayer

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.text.text

/** Alerts the player when their own slayer boss spawns, which the server announces to the quest's owner alone. */
class SlayerBossAlert(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "slayer-boss-alert", Area.ANYWHERE, Store.SLAYER, "Alert you when your slayer boss spawns.")

    init {
        feature.line("☠ SLAYER BOSS SPAWNING!") { feature.alert(text { warning("Boss Spawned") }, text { body("Your slayer boss spawned.") }) }
    }
}
