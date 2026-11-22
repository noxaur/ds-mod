package io.github.noxaur.dsmod.core.feature.end

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.text.clock
import io.github.noxaur.dsmod.core.text.count
import io.github.noxaur.dsmod.core.text.secondsLeft

/** Follows the dragon fight in the End: the egg's return, the eyes placed and the dragon's time alive. */
class DragonTimer(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "dragon-timer", Area.END, Store.END, "Show the egg timer, the eyes placed and the dragon's time alive.", hud = ::hud)

    private sealed interface Phase {
        data object Unknown : Phase

        data class Alive(val dragon: String, val since: Long) : Phase

        data class Egg(val at: Long) : Phase

        data class Eyes(val placed: Int) : Phase
    }

    private var phase: Phase by feature.world { Phase.Unknown }
    private var eyesNeeded = 8

    init {
        // `☠ The ` also starts the Endstone Protector's lines, so the head names a dragon.
        feature.line(Regex("""☠ The .+ Dragon has """), Regex("""☠ The (\w+) Dragon has (spawned!|de-spawned\.)""")) { (name, news), now ->
            phase = if (news == "spawned!") Phase.Alive(name, now) else Phase.Unknown
        }
        feature.line("The Dragon Egg is respawning", Regex("""The Dragon Egg is respawning in (\d+) seconds\.""")) { (seconds), now ->
            phase = Phase.Egg(now + (count(seconds) ?: return@line) * 1000L)
        }
        feature.line("✦ The Egg has Spawned!") { phase = Phase.Eyes(0) }
        feature.line(Regex("""» .+ Summoning Eye"""), Regex("""» .+ placed a Summoning Eye! \((\d+)/(\d+)\)""")) { (placed, needed), _ ->
            phase = Phase.Eyes(count(placed) ?: return@line)
            eyesNeeded = count(needed) ?: return@line
        }
    }

    fun hud(now: Long): HudBlock? = when (val phase = phase) {
        is Phase.Alive -> feature.block { row("${phase.dragon} Dragon") { value(clock(now - phase.since)) } }
        is Phase.Egg -> feature.block { row("Egg") { value("${secondsLeft(phase.at - now)}s") } }
        is Phase.Eyes -> feature.block { row("Eyes") { value("${phase.placed}/$eyesNeeded") } }
        Phase.Unknown -> null
    }
}
