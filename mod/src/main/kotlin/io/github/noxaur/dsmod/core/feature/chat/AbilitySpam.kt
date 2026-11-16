package io.github.noxaur.dsmod.core.feature.chat

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.text.text
import kotlinx.serialization.Serializable
import kotlin.reflect.KMutableProperty0

/** Whether the player has been told that their cooldown lines are hidden. */
@Serializable
data class AbilitySpamTold(val told: Boolean = false)

/**
 * Hides the line the server sends for each press of an ability that is still on cooldown, which floods chat
 * while the player holds the button. The first hidden line ever is answered with where to turn this off,
 * because the player did not ask for a server line to go missing.
 */
class AbilitySpam(toggle: KMutableProperty0<Boolean>) : Feature("ability-spam", toggle, Area.ANYWHERE) {
    private val told = saved(AbilitySpamTold())

    init {
        line("This ability is on cooldown for ") {
            hide()
            if (told.value.told) return@line
            told.value = AbilitySpamTold(told = true)
            notice(text { body("Hid a cooldown message. Turn off "); value("Ability Spam"); body(" in /dsmod to see them.") })
        }
    }
}
