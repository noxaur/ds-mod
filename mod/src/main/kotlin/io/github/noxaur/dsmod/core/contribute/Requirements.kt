package io.github.noxaur.dsmod.core.contribute

import io.github.noxaur.dsmod.core.backend.Request
import io.github.noxaur.dsmod.core.game.SidebarState
import io.github.noxaur.dsmod.core.game.TabState
import kotlinx.serialization.Serializable

private val FLOOR = Regex(""" \([^)]*\)$""")

/** @keep These are the server's own names and not an Area, because a request is most often for a place the mod has no feature
 * in yet. */
@Serializable
data class Places(val names: Set<String> = emptySet()) {
    fun with(tab: TabState, sidebar: SidebarState): Places {
        val area = tab.info("Area")
        val place = sidebar.place?.replace(FLOOR, "")
        if ((area == null || area in names) && (place == null || place in names)) return this
        return Places(names + listOfNotNull(area, place))
    }
}

/** @keep The backend never knows what a player has seen, so the mod checks, and a kind it does not know is not met, so an
 * older mod never offers what it cannot judge. */
object Requirements {
    fun met(kind: String, value: String, places: Places) = when (kind) {
        "area" -> value in places.names
        else -> false
    }

    fun qualifies(request: Request, places: Places) = request.requirements.all { met(it.kind, it.value, places) }
}
