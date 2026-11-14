package io.github.noxaur.dsmod.core.feature.end

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.HeadPlaced
import io.github.noxaur.dsmod.core.game.ProtectorRow
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.ui.Section
import io.github.noxaur.dsmod.core.ui.Sections
import io.github.noxaur.dsmod.core.world.Mark
import io.github.noxaur.dsmod.core.world.Point
import io.github.noxaur.dsmod.core.world.Waypoint
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty0

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
class BossBeacon(
    toggle: KMutableProperty0<Boolean>,
    private val sections: KProperty0<List<Section>>,
    private val inFight: KProperty0<Boolean>,
) : Feature("boss-beacon", toggle) {
    private var beam = emptyList<Mark>()
    private var x = 0
    private var y = 0
    private var z = 0

    init {
        on<WorldChanged> { _, _ -> beam = emptyList() }
        // The area is asked when the beam is drawn and not here, because a head can arrive with the world, ahead of the tab list.
        on<HeadPlaced> { head, _ ->
            val sameColumn = beam.isNotEmpty() && head.x == x && head.z == z
            y = if (sameColumn) minOf(y, head.y) else head.y
            x = head.x
            z = head.z
            beam = listOf(Waypoint(Point(x + 0.5, y.toDouble(), z + 0.5), "Protector"))
        }
    }

    override fun marks(now: Long): List<Mark> {
        if (tab.area != "The End") return emptyList()
        val row = tab.info("Protector")?.let(ProtectorRow::parse) ?: return beam
        val shows = when {
            row.stage == "Spawned" -> inFight.get()
            row.killsLeft == null -> true
            else -> Sections.contains(sections.get(), (ProtectorRow.CYCLE_KILLS - row.killsLeft).coerceIn(0, ProtectorRow.CYCLE_KILLS))
        }
        return if (shows) beam else emptyList()
    }
}
