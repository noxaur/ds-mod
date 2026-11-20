package io.github.noxaur.dsmod.core.feature.end

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.HeadPlaced
import io.github.noxaur.dsmod.core.game.ProtectorRow
import io.github.noxaur.dsmod.core.ui.Section
import io.github.noxaur.dsmod.core.ui.Sections
import io.github.noxaur.dsmod.core.world.Mark
import io.github.noxaur.dsmod.core.world.Point
import io.github.noxaur.dsmod.core.world.Waypoint

/**
 * A beam where the Endstone Protector will emerge. The server marks the spot with a head: it appears the moment
 * the last Protector falls, climbs one block with each tier the next one rises, and gives way to the Protector
 * itself. So the newest head is the spot, from one fight's end through the next. The beam stays on the lowest
 * block the head was seen on, while the head climbs.
 *
 * The player picks when the beam shows, as [sections] of the cycle's 200 zealot kills and [inFight] for the fight.
 * The tab list's `Protector` row says how far the cycle is. Without that row the beam shows, because a beam the
 * player did not ask for is a smaller fault than a missing one.
 */
class ProtectorSpawnBeam(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "protector-spawn-beam", Area.END, Store.END, "Show a beam where the Endstone Protector will emerge.", formerly = listOf("bossBeacon"), marks = ::marks)

    /** The parts of the Protector's cycle that show the beam, in zealots killed since the last defeat, out of 200. */
    val sections = feature.span("beamSections", "Beam Timing", "Pick when the beam shows, in zealots killed out of 200.", listOf(Section(175, 200)), max = 200, ticks = listOf(50, 100, 150))
    val inFight = feature.switch("beamInFight", "During the Fight", "Keep the beam while the Protector is up.", default = false)
    private var beam by feature.world { emptyList<Mark>() }
    private var x = 0
    private var y = 0
    private var z = 0

    init {
        feature.on<HeadPlaced> { head, _ ->
            val sameColumn = beam.isNotEmpty() && head.x == x && head.z == z
            y = if (sameColumn) minOf(y, head.y) else head.y
            x = head.x
            z = head.z
            beam = listOf(Waypoint(Point(x + 0.5, y.toDouble(), z + 0.5), "Protector"))
        }
    }

    fun marks(now: Long): List<Mark> {
        val row = feature.tab.info("Protector")?.let(ProtectorRow::parse) ?: return beam
        val shows = when {
            row.stage == "Spawned" -> inFight.value
            row.killsLeft == null -> true
            else -> Sections.contains(sections.value, (ProtectorRow.CYCLE_KILLS - row.killsLeft).coerceIn(0, ProtectorRow.CYCLE_KILLS))
        }
        return if (shows) beam else emptyList()
    }
}
