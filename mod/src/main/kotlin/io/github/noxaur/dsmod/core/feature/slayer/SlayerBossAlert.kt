package io.github.noxaur.dsmod.core.feature.slayer

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.Fights
import io.github.noxaur.dsmod.core.text.text
import kotlin.reflect.KMutableProperty0

/** Alerts the player when their own slayer boss spawns, which the server announces to the quest's owner alone. */
class SlayerBossAlert(toggle: KMutableProperty0<Boolean>) : Feature("slayer-boss-alert", toggle, Area.ANYWHERE) {
    init {
        line(Fights.SLAYER_SPAWNING) { alert(text { warning("Boss Spawned") }, text { body("Your slayer boss spawned.") }) }
    }
}
