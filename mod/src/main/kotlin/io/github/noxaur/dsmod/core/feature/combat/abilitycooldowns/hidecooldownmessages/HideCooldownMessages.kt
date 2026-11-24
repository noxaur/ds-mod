package io.github.noxaur.dsmod.core.feature.combat.abilitycooldowns.hidecooldownmessages

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Abilities
import io.github.noxaur.dsmod.core.game.Area

class HideCooldownMessages(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "hide-cooldown-messages", Area.ANYWHERE, Store.CHAT, "Hide the message that an ability is on cooldown.", formerly = listOf("abilitySpam"))

    init {
        feature.line(Abilities.REFUSAL) { feature.hide() }
    }
}
