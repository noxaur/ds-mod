package io.github.noxaur.dsmod.core.feature.loot

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.Drop
import io.github.noxaur.dsmod.core.text.text

/**
 * Alerts the player to their own drops of the tier they chose or better. A tier word no capture has shown yet
 * counts as `unparsed` and alerts nobody, so add it to [LootTier] when the counter shows one.
 */
class RareDropAlert(config: DsModConfig) :
    Feature(config, "rare-drop-alert", Area.ANYWHERE, Store.LOOT, "Alert you to drops of the chosen tier or better.", formerly = "lootAlerts") {
    /** A drop's tier, lowest first. [word] is what the server writes before `DROP!`. */
    enum class LootTier(private val label: String, val word: String) {
        RARE("Rare", "RARE"),
        EPIC("Epic", "EPIC"),
        RNGESUS("RNGesus", "RNGESUS"),
        ;

        override fun toString() = label
    }

    val least = choice("lootAlertTier", "Alert Tier", "Pick the lowest drop tier that alerts you.", LootTier.EPIC)

    init {
        line(Drop.head, { line -> Drop.parse(line)?.let { drop -> LootTier.entries.firstOrNull { it.word == drop.tier }?.let { drop to it } } }) { (drop, tier), _ ->
            if (tier >= least.value) alert(text { value(drop.item) }, text { value(drop.item); body(" dropped.") })
        }
    }
}
