package io.github.noxaur.dsmod.core.feature.end

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.text.text
import kotlin.reflect.KMutableProperty0

/** Alerts the player when a dragon says it is about to throw fireballs, which leaves them seconds to move. */
class FireballAlert(toggle: KMutableProperty0<Boolean>) : Feature("fireball-alert", toggle, Area.END) {
    // A player's chat line has one word before the colon, and a dragon's has its rank and two.
    private val fireballs = Regex("""^\[BOSS] (\w+ Dragon): .*FIREBALL!!$""")

    init {
        on<SystemMessage> { message, _ ->
            val line = message.text.plain
            if (!line.startsWith("[BOSS] ") || !line.endsWith("FIREBALL!!")) return@on
            val (dragon) = fireballs.find(line)?.destructured ?: return@on unparsed(line)
            alert(text { warning("Fireball") }, text { value(dragon); body(" is throwing fireballs.") })
        }
    }
}
