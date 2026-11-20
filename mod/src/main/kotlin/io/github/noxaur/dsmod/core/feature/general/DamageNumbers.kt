package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.FloatingText
import io.github.noxaur.dsmod.core.text.short
import io.github.noxaur.dsmod.core.text.text

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
class DamageNumbers(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "damage-numbers", Area.ANYWHERE, Store.GENERAL, "Shorten or hide the damage numbers that pop up on a hit.", default = false)

    val shown = feature.choice("damageShown", "Show", "Pick which damage numbers you see: all, only crits, or none.", DamageShown.ALL)
    val shorten = feature.switch("damageShort", "Short Numbers", "Shorten big numbers, so 1,234,567 reads 1.2M.", default = true)
    val decimals = feature.number("damageDecimals", "Decimals", "Set how many decimals a short number keeps.", 1f, 0f..3f, step = 1f, dependsOn = shorten)

    init {
        feature.on<FloatingText> { event, _ ->
            val plain = event.text.plain
            val match = SPLASH.matchEntire(plain)
            if (match == null) {
                if (CRIT in plain) feature.unparsed(plain)
                return@on
            }
            val (before, digits, after) = match.destructured
            val crit = CRIT in before || CRIT in after
            when (shown.value) {
                DamageShown.NONE -> return@on feature.hide()
                DamageShown.CRITS -> if (!crit) return@on feature.hide()
                DamageShown.ALL -> Unit
            }
            if (!shorten.value) return@on
            val number = short(digits.replace(",", "").toDouble(), decimals.value.toInt())
            if (number != digits) feature.replace(text { server(before + number + after, event.text) })
        }
    }

    private companion object {
        const val CRIT = '✧'

        /** A number, and only symbols around it: no letter, which names a mob, and no `/`, which a health bar has. */
        val SPLASH = Regex("""([^\p{L}\p{N}/\s]*)(\d[\d,]*(?:\.\d+)?)([^\p{L}\p{N}/\s]*)""")
    }
}
