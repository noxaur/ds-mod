package io.github.noxaur.dragen.core.contribute

import io.github.noxaur.dragen.core.backend.Request
import io.github.noxaur.dragen.core.game.SidebarState
import io.github.noxaur.dragen.core.game.TabState
import kotlinx.serialization.Serializable

private val FLOOR = Regex(""" \([^)]*\)$""")

@Serializable
data class Places(val names: Set<String> = emptySet()) {
    fun with(tab: TabState, sidebar: SidebarState): Places {
        val area = tab.info("Area")
        val place = sidebar.place?.replace(FLOOR, "")
        if ((area == null || area in names) && (place == null || place in names)) return this
        return Places(names + listOfNotNull(area, place))
    }
}

object Requirements {
    fun met(kind: String, value: String, places: Places) = when (kind) {
        "area" -> value in places.names
        else -> false
    }

    fun qualifies(request: Request, places: Places) = request.requirements.all { met(it.kind, it.value, places) }
}
