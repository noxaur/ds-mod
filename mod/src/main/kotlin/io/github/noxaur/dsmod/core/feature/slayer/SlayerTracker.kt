package io.github.noxaur.dsmod.core.feature.slayer

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.text.clock
import kotlinx.serialization.Serializable

/** Every slayer boss the player has slain. */
@Serializable
data class SlayerKills(val slain: Int = 0)

/**
 * Counts the slayer bosses the player slays, which the server announces to the quest's owner alone, and times each
 * from its spawn to its death. A failed quest sends no such line, so it counts for nothing. The quest's lines are
 * indented, which a player's chat line never is, and give the quest's goal and the XP left to the next level. The
 * readout stays hidden until the first quest or boss, so it never shows for a player who does no slayers.
 */
class SlayerTracker(config: DsModConfig) :
    Feature(config, "slayer-tracker", Area.ANYWHERE, Store.SLAYER, "Count the slayer bosses you slay and time each from spawn to slain.") {
    private val allTime = saved(SlayerKills())
    private var slain = 0
    private var timed = 0
    private var timedMillis = 0L
    private var spawnedAt: Long? by world { null }
    private var seen = false
    private var quest: String? = null
    private var level: String? = null
    private var toNext = 0L

    init {
        line("   » Slay ", Regex("""   » Slay ([\d,]+) Combat XP worth of (.+)\.""")) { (xp, mobs), _ ->
            seen = true
            quest = "$xp XP of $mobs"
        }
        for (end in listOf("  SLAYER QUEST COMPLETE!", "  SLAYER QUEST FAILED")) {
            line(end) {
                quest = null
                spawnedAt = null
            }
        }
        line(Regex("""   .+ Slayer LVL """), Regex("""   (.+ Slayer) LVL (\d+) - Next LVL in ([\d,]+) XP!""")) { (slayer, at, left), _ ->
            level = "$slayer $at"
            toNext = left.replace(",", "").toLong()
        }
        line(Regex("""☠ .*SLAYER BOSS"""), Regex("""☠ (?:NICE! )?SLAYER BOSS (SPAWNING|SLAIN)!""")) { (event), now ->
            seen = true
            if (event == "SPAWNING") {
                spawnedAt = now
                return@line
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
            if (spawnedAt != null) row("Quest") { value("Boss") } else quest?.let { row("Quest") { value(it) } }
            level?.let { row(it) { value(toNext); muted(" XP to go") } }
            row("Slain") { value(slain.toLong()) }
            row("Spawn to Slain") { value(if (timed > 0) clock(timedMillis / timed + 500) else "-") }
            row("All-Time") { value(allTime.value.slain.toLong()) }
        }
    }
}
