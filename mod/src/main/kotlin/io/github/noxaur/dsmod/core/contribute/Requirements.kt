package io.github.noxaur.dsmod.core.contribute

import io.github.noxaur.dsmod.core.backend.Request
import io.github.noxaur.dsmod.core.game.SidebarState
import io.github.noxaur.dsmod.core.game.TabState
import kotlinx.serialization.Serializable

/** A dungeon's floor after its name, as in `The Catacombs (F1)`. */
private val FLOOR = Regex(""" \([^)]*\)$""")

/**
 * The places the player has been, kept between sessions so that a request can ask for one they visited earlier. A
 * place is what the tab list's `Area` row or the sidebar's `⏣` line named, the latter without a dungeon's floor. These are the
 * server's own names and not an [io.github.noxaur.dsmod.core.game.Area], because a request is most often for a place
 * the mod has no feature in yet.
 */
@Serializable
data class Places(val names: Set<String> = emptySet()) {
    /** These places and wherever [tab] and [sidebar] put the player now, or this when nothing is new. */
    fun with(tab: TabState, sidebar: SidebarState): Places {
        val area = tab.info("Area")
        val place = sidebar.place?.replace(FLOOR, "")
        if ((area == null || area in names) && (place == null || place in names)) return this
        return Places(names + listOfNotNull(area, place))
    }
}

/**
 * Whether this player may be offered a request. The backend never knows what a player has seen, so the mod checks.
 * A kind the mod does not know is not met, so an older mod never offers what it cannot judge. A new kind is one more
 * branch in [met].
 */
object Requirements {
    fun met(kind: String, value: String, places: Places) = when (kind) {
        "area" -> value in places.names
        else -> false
    }

    fun qualifies(request: Request, places: Places) = request.requirements.all { met(it.kind, it.value, places) }
}
