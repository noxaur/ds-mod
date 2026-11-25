package io.github.noxaur.dragen.core.feature.inventory.items.raredropalert

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area
import io.github.noxaur.dragen.core.game.Drop
import io.github.noxaur.dragen.core.text.text

class RareDropAlert(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "rare-drop-alert", Area.ANYWHERE, Store.LOOT, "Alert you with a title and sound for drops of your tier or higher.", formerly = listOf("lootAlerts"))

    enum class LootTier(private val label: String, val word: String) {
        RARE("Rare", "RARE"),
        EPIC("Epic", "EPIC"),
        RNGESUS("RNGesus", "RNGESUS"),
        ;

        override fun toString() = label
    }

    val least = feature.choice("lootAlertTier", "Alert Tier", "Pick the lowest tier that alerts you.", LootTier.EPIC)

    init {
        feature.line(Drop.head, { line -> Drop.parse(line)?.let { drop -> LootTier.entries.firstOrNull { it.word == drop.tier }?.let { drop to it } } }) { (drop, tier), _ ->
            if (tier >= least.value) feature.alert(text { value(drop.item) }, text { value(drop.item); body(" dropped.") })
        }
    }
}
