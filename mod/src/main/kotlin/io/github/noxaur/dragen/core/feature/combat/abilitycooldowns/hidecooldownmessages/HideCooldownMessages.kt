package io.github.noxaur.dragen.core.feature.combat.abilitycooldowns.hidecooldownmessages

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Abilities
import io.github.noxaur.dragen.core.game.Area

class HideCooldownMessages(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "hide-cooldown-messages", Area.ANYWHERE, Store.CHAT, "Hide the message that an ability is on cooldown.", formerly = listOf("abilitySpam"))

    init {
        feature.line(Abilities.REFUSAL) { feature.hide() }
    }
}
