package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.FloatingText
import io.github.noxaur.dsmod.core.text.short
import io.github.noxaur.dsmod.core.text.text
import kotlin.reflect.KMutableProperty0

/** Which damage numbers the player sees. */
enum class DamageShown(private val label: String) {
    ALL("All"),
    CRITS("Crits Only"),
    NONE("None"),
    ;

    override fun toString() = label
}

/**
 * Shortens the damage numbers that pop up over a mob the player or anyone near them hits, or hides them. The server
 * floats each as a text display for about a second: a hit reads `74,540,375` in gray, and a crit `✧1,461,845✧` with a
 * gradient across it. A shortened number keeps that gradient.
 */
class DamageNumbers(
    toggle: KMutableProperty0<Boolean>,
    private val shown: KMutableProperty0<DamageShown>,
    private val shorten: KMutableProperty0<Boolean>,
    private val decimals: KMutableProperty0<Float>,
) : Feature("damage-numbers", toggle, Area.ANYWHERE) {
    init {
        on<FloatingText> { event, _ ->
            val plain = event.text.plain
            val match = SPLASH.matchEntire(plain)
            if (match == null) {
                if (CRIT in plain) unparsed(plain)
                return@on
            }
            val (before, digits, after) = match.destructured
            val crit = CRIT in before || CRIT in after
            when (shown.get()) {
                DamageShown.NONE -> return@on hide()
                DamageShown.CRITS -> if (!crit) return@on hide()
                DamageShown.ALL -> Unit
            }
            if (!shorten.get()) return@on
            val number = short(digits.replace(",", "").toDouble(), decimals.get().toInt())
            if (number != digits) replace(text { server(before + number + after, event.text) })
        }
    }

    private companion object {
        const val CRIT = '✧'

        /** A number, and only symbols around it: no letter, which names a mob, and no `/`, which a health bar has. */
        val SPLASH = Regex("""([^\p{L}\p{N}/\s]*)(\d[\d,]*(?:\.\d+)?)([^\p{L}\p{N}/\s]*)""")
    }
}
