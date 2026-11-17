package io.github.noxaur.dsmod.core.feature.chat

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Abilities
import io.github.noxaur.dsmod.core.game.Area

/**
 * Hides the line the server sends for each press of an ability that is still on cooldown, which floods chat
 * while the player holds the button.
 */
class HideCooldownMessages(config: DsModConfig) :
    Feature(config, "hide-cooldown-messages", Area.ANYWHERE, Store.CHAT, "Hide the message that an ability is on cooldown.", formerly = "abilitySpam") {
    init {
        line(Abilities.REFUSAL) { hide() }
    }
}
