package io.github.noxaur.dsmod.core.feature.chat

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import kotlin.reflect.KMutableProperty0

/**
 * Hides the line the server sends for each press of an ability that is still on cooldown, which floods chat
 * while the player holds the button.
 */
class AbilitySpam(toggle: KMutableProperty0<Boolean>) : Feature("ability-spam", toggle, Area.ANYWHERE) {
    init {
        line("This ability is on cooldown for ") { hide() }
    }
}
