package io.github.noxaur.dsmod.core.feature.end

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.HeadPlaced
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.world.Mark
import io.github.noxaur.dsmod.core.world.Point
import io.github.noxaur.dsmod.core.world.Waypoint
import kotlin.reflect.KMutableProperty0

/**
 * A beam where the Endstone Protector will emerge. The server marks the spot with a head: it appears the moment
 * the last Protector falls, climbs one block with each tier the next one rises, and gives way to the Protector
 * itself. So the newest head in The End is the spot, from one fight's end through the next. The beam stays on
 * the ground the first head stood on, while the head climbs.
 */
class BossBeacon(toggle: KMutableProperty0<Boolean>) : Feature("boss-beacon", toggle) {
    private var marks = emptyList<Mark>()
    private var x = 0
    private var y = 0
    private var z = 0

    init {
        on<WorldChanged> { _, _ -> marks = emptyList() }
        on<HeadPlaced> { head, _ ->
            if (tab.area != "The End") return@on
            // The same column one block up is the same spot at the next tier.
            val climbed = marks.isNotEmpty() && head.x == x && head.z == z && head.y > y
            x = head.x
            z = head.z
            if (!climbed) y = head.y
            marks = listOf(Waypoint(Point(x + 0.5, y.toDouble(), z + 0.5), "Protector"))
        }
    }

    override fun marks(now: Long): List<Mark> = if (tab.area == "The End") marks else emptyList()
}
