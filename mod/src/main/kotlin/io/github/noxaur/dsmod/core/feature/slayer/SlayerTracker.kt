package io.github.noxaur.dsmod.core.feature.slayer

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.SlayerLine
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.text.clock
import io.github.noxaur.dsmod.core.text.count
import kotlinx.serialization.Serializable

@Serializable
data class SlayerKills(val slain: Int = 0)

/** @keep A failed quest sends no such line, so it counts for nothing. The quest's lines are indented, which a
 * player's chat line never is */
class SlayerTracker(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "slayer-tracker", Area.ANYWHERE, Store.SLAYER, "Count the slayer bosses you slay and time each from spawn to slain.", hud = ::hud)

    private val allTime = feature.profiled(SlayerKills())
    private var slain = 0
    private var timed = 0
    private var timedMillis = 0L
    private var spawnedAt: Long? by feature.world { null }
    private var seen = false
    private var quest: String? = null
    private var level: String? = null
    private var toNext = 0L

    init {
        feature.line("   » Slay ", Regex("""   » Slay ([\d,]+) Combat XP worth of (.+)\.""")) { (xp, mobs), _ ->
            seen = true
            quest = "$xp XP of $mobs"
        }
        for (end in listOf("  SLAYER QUEST COMPLETE!", "  SLAYER QUEST FAILED")) {
            feature.line(end) {
                quest = null
                spawnedAt = null
            }
        }
        feature.line(Regex("""   .+ Slayer LVL """), Regex("""   (.+ Slayer) LVL (\d+) - Next LVL in ([\d,]+) XP!""")) { (slayer, at, left), _ ->
            toNext = count(left)?.toLong() ?: return@line
            level = "$slayer $at"
        }
        feature.line(SlayerLine.head, SlayerLine::parse) { event, now ->
            seen = true
            if (event == SlayerLine.SPAWNING) {
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

    fun hud(now: Long): HudBlock? {
        if (!seen) return null
        return feature.block {
            if (spawnedAt != null) row("Quest") { value("Boss") } else quest?.let { row("Quest") { value(it) } }
            level?.let { row(it) { value(toNext); muted(" XP to go") } }
            row("Slain") { value(slain.toLong()) }
            row("Spawn to Slain") { value(if (timed > 0) clock(timedMillis / timed + 500) else "-") }
            row("All-Time") { value(allTime.value.slain.toLong()) }
        }
    }
}
