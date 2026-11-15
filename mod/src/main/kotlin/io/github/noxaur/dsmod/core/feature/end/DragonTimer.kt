package io.github.noxaur.dsmod.core.feature.end

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.text.clock
import kotlin.reflect.KMutableProperty0

/** Follows the dragon fight in the End: the egg's return, the eyes placed and the dragon's time alive. */
class DragonTimer(toggle: KMutableProperty0<Boolean>) : Feature("dragon-timer", toggle, Area.END) {
    private var dragon: String? = null
    private var spawnedAt = 0L
    private var eggAt: Long? = null
    private var eyes: Int? = null
    private var eyesNeeded = 8

    // Each pattern is anchored at the start, where a player's chat line has their name.
    private val dragonNews = Regex("""^☠ The (\w+) Dragon has (spawned!|de-spawned\.)$""")
    private val eggRespawning = Regex("""^The Dragon Egg is respawning in (\d+) seconds\.$""")
    private val eyePlaced = Regex("""^» .+ placed a Summoning Eye! \((\d+)/(\d+)\)$""")

    init {
        on<WorldChanged> { _, _ ->
            dragon = null
            eggAt = null
            eyes = null
        }
        on<SystemMessage> { message, now ->
            val line = message.text.plain
            when {
                line.startsWith("☠ The ") && " Dragon has " in line -> {
                    val (name, news) = dragonNews.find(line)?.destructured ?: return@on unparsed(line)
                    dragon = name.takeIf { news == "spawned!" }
                    spawnedAt = now
                    eyes = null
                }
                line.startsWith("The Dragon Egg is respawning") -> {
                    val (seconds) = eggRespawning.find(line)?.destructured ?: return@on unparsed(line)
                    eggAt = now + seconds.toLong() * 1000
                    dragon = null
                }
                line == "✦ The Egg has Spawned!" -> {
                    eggAt = null
                    eyes = 0
                }
                line.startsWith("» ") && "Summoning Eye" in line -> {
                    val (placed, needed) = eyePlaced.find(line)?.destructured ?: return@on unparsed(line)
                    eggAt = null
                    eyes = placed.toInt()
                    eyesNeeded = needed.toInt()
                }
            }
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
