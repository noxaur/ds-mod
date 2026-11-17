package io.github.noxaur.dsmod.core.feature.end

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.text.clock

/** Follows the dragon fight in the End: the egg's return, the eyes placed and the dragon's time alive. */
class DragonTimer(config: DsModConfig) :
    Feature(config, "dragon-timer", Area.END, Store.END, "Show the egg timer, the eyes placed and the dragon's time alive.") {
    private var dragon: String? = null
    private var spawnedAt = 0L
    private var eggAt: Long? = null
    private var eyes: Int? = null
    private var eyesNeeded = 8

    init {
        on<WorldChanged> { _, _ ->
            dragon = null
            eggAt = null
            eyes = null
        }
        // `☠ The ` also starts the Endstone Protector's lines, so the head names a dragon.
        line(Regex("""☠ The .+ Dragon has """), Regex("""☠ The (\w+) Dragon has (spawned!|de-spawned\.)""")) { (name, news), now ->
            dragon = name.takeIf { news == "spawned!" }
            spawnedAt = now
            eyes = null
        }
        line("The Dragon Egg is respawning", Regex("""The Dragon Egg is respawning in (\d+) seconds\.""")) { (seconds), now ->
            eggAt = now + seconds.toLong() * 1000
            dragon = null
        }
        line("✦ The Egg has Spawned!") {
            eggAt = null
            eyes = 0
        }
        line(Regex("""» .+ Summoning Eye"""), Regex("""» .+ placed a Summoning Eye! \((\d+)/(\d+)\)""")) { (placed, needed), _ ->
            eggAt = null
            eyes = placed.toInt()
            eyesNeeded = needed.toInt()
        }
    }

    override fun hud(now: Long): HudBlock? {
        val egg = eggAt
        return when {
            dragon != null -> block { row("$dragon Dragon") { value(clock(now - spawnedAt)) } }
            egg != null -> block { row("Egg") { value("${((egg - now + 999) / 1000).coerceAtLeast(0)}s") } }
            eyes != null -> block { row("Eyes") { value("$eyes/$eyesNeeded") } }
            else -> null
        }
    }
}
