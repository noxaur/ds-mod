package io.github.noxaur.dsmod.core.feature.end

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.text.text
import kotlin.reflect.KMutableProperty0

/** Alerts the player when a dragon says it is about to throw fireballs, which leaves them seconds to move. */
class FireballAlert(toggle: KMutableProperty0<Boolean>) : Feature("fireball-alert", toggle, Area.END) {
    init {
        line(Regex("""\[BOSS] .*FIREBALL!!$"""), Regex("""\[BOSS] (\w+ Dragon): .*FIREBALL!!""")) { (dragon), _ ->
            alert(text { warning("Fireball") }, text { value(dragon); body(" is throwing fireballs.") })
        }
    }
}
