package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.text.text
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty0

/**
 * Alerts the player to a moment that needs them within seconds: a dragon about to throw fireballs, and their
 * own slayer boss spawning, which the server announces to the quest's owner alone. Each has its own setting.
 */
class Alerts(
    toggle: KMutableProperty0<Boolean>,
    private val fireball: KProperty0<Boolean>,
    private val slayerBoss: KProperty0<Boolean>,
) : Feature("alerts", toggle) {
    // A player's chat line has one word before the colon, and a dragon's has its rank and two.
    private val fireballs = Regex("""^\[BOSS] (\w+ Dragon): .*FIREBALL!!$""")

    init {
        on<SystemMessage> { message, _ ->
            val line = message.text.plain
            when {
                line.startsWith("[BOSS] ") && line.endsWith("FIREBALL!!") -> {
                    val (dragon) = fireballs.find(line)?.destructured ?: return@on count("unparsed")
                    if (fireball.get()) alert(text { warning("Fireball") }, text { value(dragon); body(" is throwing fireballs.") })
                }
                line == "☠ SLAYER BOSS SPAWNING!" ->
                    if (slayerBoss.get()) alert(text { warning("Boss Spawned") }, text { body("Your slayer boss spawned.") })
            }
        }
    }
}
