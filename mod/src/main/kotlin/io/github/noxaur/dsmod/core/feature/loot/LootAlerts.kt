package io.github.noxaur.dsmod.core.feature.loot

import io.github.noxaur.dsmod.core.config.DsModConfig.LootTier
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.Drop
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.text.text
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty0

/**
 * Alerts the player to their own drops of the tier they chose or better. A tier word no capture has shown yet
 * counts as `unparsed` and alerts nobody, so add it to [LootTier] when the counter shows one.
 */
class LootAlerts(toggle: KMutableProperty0<Boolean>, private val least: KProperty0<LootTier>) : Feature("loot-alerts", toggle, Area.ANYWHERE) {
    init {
        on<SystemMessage> { message, _ ->
            val drop = Drop.parse(message.text.plain) ?: return@on
            val tier = LootTier.entries.firstOrNull { it.word == drop.tier } ?: return@on count("unparsed")
            if (tier >= least.get()) alert(text { value(drop.item) }, text { value(drop.item); body(" dropped.") })
        }
    }
}
