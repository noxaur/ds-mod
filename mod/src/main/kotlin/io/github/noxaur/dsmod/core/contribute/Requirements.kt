package io.github.noxaur.dsmod.core.contribute

import io.github.noxaur.dsmod.core.backend.Request
import kotlinx.serialization.Serializable

/** The areas the tab list has shown this player, kept between sessions so that a request can ask for one they visited earlier. */
@Serializable
data class AreasSeen(val names: Set<String> = emptySet())

/**
 * Whether this player may be offered a request. The backend never knows what a player has seen, so the mod checks.
 * A kind the mod does not know is not met, so an older mod never offers what it cannot judge. A new kind is one more
 * branch in [met].
 */
object Requirements {
    fun met(kind: String, value: String, areas: Set<String>) = when (kind) {
        "area" -> value in areas
        else -> false
    }

    fun qualifies(request: Request, areas: Set<String>) = request.requirements.all { met(it.kind, it.value, areas) }
}
