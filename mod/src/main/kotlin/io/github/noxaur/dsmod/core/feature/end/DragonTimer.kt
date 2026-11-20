package io.github.noxaur.dsmod.core.feature.end

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.text.clock

/** Follows the dragon fight in the End: the egg's return, the eyes placed and the dragon's time alive. */
class DragonTimer(config: DsModConfig) :
    Feature(config, "dragon-timer", Area.END, Store.END, "Show the egg timer, the eyes placed and the dragon's time alive.") {
    private sealed interface Phase {
        data object Unknown : Phase

        data class Alive(val dragon: String, val since: Long) : Phase

        data class Egg(val at: Long) : Phase

        data class Eyes(val placed: Int) : Phase
    }

    private var phase: Phase by world { Phase.Unknown }
    private var eyesNeeded = 8

    init {
        // `☠ The ` also starts the Endstone Protector's lines, so the head names a dragon.
        line(Regex("""☠ The .+ Dragon has """), Regex("""☠ The (\w+) Dragon has (spawned!|de-spawned\.)""")) { (name, news), now ->
            phase = if (news == "spawned!") Phase.Alive(name, now) else Phase.Unknown
        }
        line("The Dragon Egg is respawning", Regex("""The Dragon Egg is respawning in (\d+) seconds\.""")) { (seconds), now ->
            phase = Phase.Egg(now + seconds.toLong() * 1000)
        }
        line("✦ The Egg has Spawned!") { phase = Phase.Eyes(0) }
        line(Regex("""» .+ Summoning Eye"""), Regex("""» .+ placed a Summoning Eye! \((\d+)/(\d+)\)""")) { (placed, needed), _ ->
            phase = Phase.Eyes(placed.toInt())
            eyesNeeded = needed.toInt()
        }
    }

    override fun hud(now: Long): HudBlock? = when (val phase = phase) {
        is Phase.Alive -> block { row("${phase.dragon} Dragon") { value(clock(now - phase.since)) } }
        is Phase.Egg -> block { row("Egg") { value("${((phase.at - now + 999) / 1000).coerceAtLeast(0)}s") } }
        is Phase.Eyes -> block { row("Eyes") { value("${phase.placed}/$eyesNeeded") } }
        Phase.Unknown -> null
    }
}
