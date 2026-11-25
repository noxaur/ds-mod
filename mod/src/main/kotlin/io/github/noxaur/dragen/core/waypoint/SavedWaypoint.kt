package io.github.noxaur.dragen.core.waypoint

import com.google.gson.annotations.Expose
import io.github.noxaur.dragen.core.world.Point
import io.github.noxaur.dragen.core.world.Waypoint
import io.github.noxaur.dragen.core.world.WaypointLook
import kotlinx.serialization.Serializable

/** @keep Two areas on the server share the same coordinates, so a waypoint shows only in the area it was made in. */
@Serializable
data class SavedWaypoint(
    @Expose val name: String = "",
    @Expose val x: Int = 0,
    @Expose val y: Int = 0,
    @Expose val z: Int = 0,
    @Expose val area: String = "",
    @Expose val color: Int = 0,
    @Expose val look: WaypointLook = WaypointLook.BEAM,
    @Expose val distance: Boolean = true,
    @Expose val throughWalls: Boolean = true,
    @Expose val enabled: Boolean = true,
    @Expose val group: String = "",
) {
    val title: String get() = name.trim().ifEmpty { position }

    val position: String get() = "$x $y $z"

    fun shows(area: String?) = this.area.isEmpty() || this.area == area

    fun sameSpot(other: SavedWaypoint) = x == other.x && y == other.y && z == other.z && area == other.area

    fun mark() = Waypoint(Point(x + 0.5, y.toDouble(), z + 0.5), name.trim(), color = color, look = look, distance = distance, throughWalls = throughWalls)

    companion object {
        const val NAME_LENGTH = 32

        const val AREA_LENGTH = 32
    }
}
