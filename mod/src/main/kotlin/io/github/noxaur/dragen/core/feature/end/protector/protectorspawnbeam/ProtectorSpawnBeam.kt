package io.github.noxaur.dragen.core.feature.end.protector.protectorspawnbeam

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area
import io.github.noxaur.dragen.core.game.HeadPlaced
import io.github.noxaur.dragen.core.game.ProtectorRow
import io.github.noxaur.dragen.core.game.ProtectorStage
import io.github.noxaur.dragen.core.ui.Section
import io.github.noxaur.dragen.core.ui.Sections
import io.github.noxaur.dragen.core.world.Mark
import io.github.noxaur.dragen.core.world.Point
import io.github.noxaur.dragen.core.world.Waypoint

/** @keep The server marks the spot with a head that appears when the last Protector falls and climbs one block per tier, so the newest head is the spot. */
class ProtectorSpawnBeam(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "protector-spawn-beam", Area.END, Store.END, "Show a beam where the Endstone Protector will emerge.", formerly = listOf("bossBeacon"), marks = ::marks)

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
            row.stage == ProtectorStage.SPAWNED -> inFight.value
            row.killsLeft == null -> true
            else -> Sections.contains(sections.value, (ProtectorRow.CYCLE_KILLS - row.killsLeft).coerceIn(0, ProtectorRow.CYCLE_KILLS))
        }
        return if (shows) beam else emptyList()
    }
}
