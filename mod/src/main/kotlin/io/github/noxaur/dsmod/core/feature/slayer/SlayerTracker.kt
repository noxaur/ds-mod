package io.github.noxaur.dsmod.core.feature.slayer

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.text.clock
import kotlinx.serialization.Serializable
import kotlin.reflect.KMutableProperty0

/** Every slayer boss the player has slain. */
@Serializable
data class SlayerKills(val slain: Int = 0)

/**
 * Counts the slayer bosses the player slays, which the server announces to the quest's owner alone, and times each
 * from its spawn to its death. A failed quest sends no such line, so it counts for nothing. The readout stays hidden
 * until the first boss spawns, so it never shows for a player who does no slayers.
 */
class SlayerTracker(toggle: KMutableProperty0<Boolean>) : Feature("slayer-tracker", toggle) {
    private val allTime = saved(SlayerKills())
    private var slain = 0
    private var timed = 0
    private var timedMillis = 0L
    private var spawnedAt: Long? = null
    private var seen = false

    // Anchored at the start, where a player's chat line has their name.
    private val boss = Regex("""^☠ (?:NICE! )?SLAYER BOSS (SPAWNING|SLAIN)!$""")

    init {
        on<WorldChanged> { _, _ -> spawnedAt = null }
        on<SystemMessage> { message, now ->
            val line = message.text.plain
            if (!line.startsWith("☠ ") || "SLAYER BOSS" !in line) return@on
            val (event) = boss.find(line)?.destructured ?: return@on count("unparsed")
            seen = true
            if (event == "SPAWNING") {
                spawnedAt = now
                return@on
            }
            slain++
            allTime.value = SlayerKills(allTime.value.slain + 1)
            spawnedAt?.let {
                timed++
                timedMillis += now - it
            }
            spawnedAt = null
        }
    }

    override fun hud(now: Long): HudBlock? {
        if (!seen) return null
        return block {
            row("Slain") { value(slain.toLong()) }
            row("Spawn to Slain") { value(if (timed > 0) clock(timedMillis / timed + 500) else "-") }
            row("All-Time") { value(allTime.value.slain.toLong()) }
        }
    }
}
