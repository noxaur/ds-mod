package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Grouped
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.waypoint.SavedWaypoint
import io.github.noxaur.dsmod.core.waypoint.WaypointKind
import io.github.noxaur.dsmod.core.world.FILL_ALPHA
import io.github.noxaur.dsmod.core.world.Mark
import io.github.noxaur.dsmod.core.world.WaypointLook

class Waypoints(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "waypoints", Area.ANYWHERE, Store.GENERAL, "Show the waypoints you save, and the ones you click in chat.", marks = { marks() })

    val chatLinks = feature.switch("waypointChatLinks", "Chat Links", "Make coordinates in chat clickable, to show or save them.", default = true)

    val color = feature.color("waypointColor", "Color", "Pick the color new waypoints start with.", Role.VALUE.argb(FILL_ALPHA))

    val look = feature.choice("waypointLook", "Look", "Pick how new waypoints are drawn.", WaypointLook.BEAM)

    val saved = feature.stored<List<SavedWaypoint>>("savedWaypoints", emptyList())

    val groups = feature.stored<List<String>>("waypointGroups", emptyList())

    val collapsedGroups = feature.stored<List<String>>("collapsedWaypointGroups", emptyList())

    val offGroups = feature.stored<List<String>>("offWaypointGroups", emptyList())

    var position: () -> Triple<Int, Int, Int>? = { null }

    private var shown by feature.world { emptyList<SavedWaypoint>() }

    private var built: List<Mark> = emptyList()
    private var builtFrom: List<Any?> = emptyList()

    init {
        feature.rows {
            items(Grouped.bind(saved, groups, collapsedGroups, offGroups), "Waypoints", "Name each waypoint, set where it is and pick how it looks.", WaypointKind(::fresh))
        }
    }

    private fun fresh(group: String) = here(group = group) ?: SavedWaypoint(area = feature.areaName.orEmpty(), color = color.value, look = look.value, group = group)

    fun here(name: String = "", group: String = ""): SavedWaypoint? {
        val (x, y, z) = position() ?: return null
        return SavedWaypoint(name, x, y, z, feature.areaName.orEmpty(), color.value, look.value, group = group)
    }

    fun styled(waypoint: SavedWaypoint) = waypoint.copy(color = color.value, look = look.value, area = waypoint.area.ifEmpty { feature.areaName.orEmpty() })

    fun show(waypoint: SavedWaypoint) {
        shown = (listOf(waypoint) + shown.filterNot { it.sameSpot(waypoint) }).take(MAX_SHOWN)
        feature.redraw()
    }

    fun save(waypoint: SavedWaypoint): Boolean {
        shown = shown.filterNot { it.sameSpot(waypoint) }
        feature.redraw()
        if (saved.value.any { it.sameSpot(waypoint) && it.name == waypoint.name }) return false
        saved.value = saved.value + waypoint.copy(group = "")
        return true
    }

    fun named(name: String): SavedWaypoint? {
        val wanted = name.trim().ifEmpty { return null }
        val area = feature.areaName
        return saved.value.filter { it.name.trim().equals(wanted, ignoreCase = true) }.let { same -> same.firstOrNull { it.shows(area) } ?: same.firstOrNull() }
    }

    fun names(): List<String> = saved.value.map { it.name.trim() }.filter { it.isNotEmpty() }.distinct()

    private fun marks(): List<Mark> {
        val at = position()
        val area = feature.areaName
        if (at != null && shown.any { reached(it, at, area) }) shown = shown.filterNot { reached(it, at, area) }
        val from = listOf(saved.value, offGroups.value, shown, area)
        if (from == builtFrom) return built
        val off = offGroups.value
        built = (saved.value.filter { it.enabled && it.group !in off && it.shows(area) } + shown.filter { it.shows(area) }).map(SavedWaypoint::mark)
        builtFrom = from
        return built
    }

    private fun reached(waypoint: SavedWaypoint, at: Triple<Int, Int, Int>, area: String?): Boolean {
        if (!waypoint.shows(area)) return false
        val dx = (waypoint.x - at.first).toLong()
        val dy = (waypoint.y - at.second).toLong()
        val dz = (waypoint.z - at.third).toLong()
        return dx * dx + dy * dy + dz * dz <= REACH * REACH
    }

    private companion object {
        const val MAX_SHOWN = 8

        const val REACH = 5
    }
}
