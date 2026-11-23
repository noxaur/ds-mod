package io.github.noxaur.dsmod.core.feature.loot

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.Drop
import io.github.noxaur.dsmod.core.text.text

class RareDropAlert(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "rare-drop-alert", Area.ANYWHERE, Store.LOOT, "Alert you to drops of the chosen tier or better.", formerly = listOf("lootAlerts"))

    enum class LootTier(private val label: String, val word: String) {
        RARE("Rare", "RARE"),
        EPIC("Epic", "EPIC"),
        RNGESUS("RNGesus", "RNGESUS"),
        ;

        override fun toString() = label
    }

    val least = feature.choice("lootAlertTier", "Alert Tier", "Pick the lowest drop tier that alerts you.", LootTier.EPIC)

    init {
        feature.line(Drop.head, { line -> Drop.parse(line)?.let { drop -> LootTier.entries.firstOrNull { it.word == drop.tier }?.let { drop to it } } }) { (drop, tier), _ ->
            if (tier >= least.value) feature.alert(text { value(drop.item) }, text { value(drop.item); body(" dropped.") })
        }
    }
}
