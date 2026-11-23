package io.github.noxaur.dsmod.core.waypoint

import com.google.gson.annotations.Expose
import io.github.noxaur.dsmod.core.config.Cell
import io.github.noxaur.dsmod.core.config.ItemKind
import io.github.noxaur.dsmod.core.config.Share
import io.github.noxaur.dsmod.core.world.WaypointLook

// @keep A new waypoint starts where the player stands, in their area, so `fresh` reads the game.
class WaypointKind(private val fresh: (group: String) -> SavedWaypoint) : ItemKind<SavedWaypoint> {
    override val noun = "waypoint"
    override val verb = "show"
    override val first = "General"
    override val groupLength = GROUP_LENGTH
    override val opens = NAME

    override val cells: List<Cell<SavedWaypoint>> = listOf(
        Cell.Check("On", SavedWaypoint::enabled) { w, on -> w.copy(enabled = on) },
        Cell.Text(NAME, SavedWaypoint::name, { w, name -> w.copy(name = WaypointText.clean(name)) }, hint = NAME, max = SavedWaypoint.NAME_LENGTH),
        // @keep A position that does not parse yet keeps the last one, so typing it digit by digit never moves the waypoint to 0.
        Cell.Text("Position", SavedWaypoint::position, { w, typed ->
            WaypointText.position(typed)?.let { (x, y, z) -> w.copy(x = x, y = y, z = z) } ?: w
        }, hint = "x y z", max = POSITION_LENGTH, width = 72),
        Cell.Color("Color", SavedWaypoint::color) { w, color -> w.copy(color = color) },
        Cell.Pick("Look", SavedWaypoint::look, { w, look -> w.copy(look = look) }, WaypointLook.entries, WaypointLook::toString, width = 50),
    )

    override val details: List<Cell<SavedWaypoint>> = listOf(
        Cell.Text("Area", SavedWaypoint::area, { w, area -> w.copy(area = WaypointText.clean(area)) }, hint = "Anywhere", max = SavedWaypoint.AREA_LENGTH, width = 96),
        Cell.Check("Distance", SavedWaypoint::distance) { w, on -> w.copy(distance = on) },
        Cell.Check("Through Walls", SavedWaypoint::throughWalls) { w, on -> w.copy(throughWalls = on) },
    )

    override fun group(item: SavedWaypoint) = item.group

    override fun regroup(item: SavedWaypoint, group: String) = item.copy(group = group)

    override fun blank(group: String) = fresh(group)

    override fun title(item: SavedWaypoint) = item.title

    override fun on(item: SavedWaypoint) = item.enabled

    override fun switched(item: SavedWaypoint, on: Boolean) = item.copy(enabled = on)

    override fun clashes(item: SavedWaypoint, live: List<SavedWaypoint>) = emptyList<SavedWaypoint>()

    override fun encode(group: String, items: List<SavedWaypoint>): String = Share.encode(PREFIX, Shared(group, items.map { it.copy(group = "") }))

    @Suppress("USELESS_CAST", "SENSELESS_COMPARISON") // @keep Gson writes null into a field the text gets wrong.
    override fun decode(text: String): Pair<String, List<SavedWaypoint>>? {
        val shared = Share.decode(PREFIX, text, Shared::class.java) ?: return null
        val waypoints = (shared.waypoints as List<SavedWaypoint?>?).orEmpty().filterNotNull().take(MAX_WAYPOINTS).mapNotNull {
            val (x, y, z) = WaypointText.position("${it.x} ${it.y} ${it.z}") ?: return@mapNotNull null
            SavedWaypoint(
                name = WaypointText.clean((it.name as String?).orEmpty()).take(SavedWaypoint.NAME_LENGTH),
                x = x,
                y = y,
                z = z,
                area = WaypointText.clean((it.area as String?).orEmpty()).take(SavedWaypoint.AREA_LENGTH),
                color = it.color,
                look = (it.look as WaypointLook?) ?: WaypointLook.BEAM,
                distance = it.distance,
                throughWalls = it.throughWalls,
                enabled = it.enabled,
            )
        }
        return (shared.group as String?).orEmpty().trim().take(GROUP_LENGTH) to waypoints
    }

    private data class Shared(@Expose val group: String = "", @Expose val waypoints: List<SavedWaypoint> = emptyList())

    private companion object {
        const val PREFIX = "dsmod-waypoints:"
        const val NAME = "Name"
        const val GROUP_LENGTH = 32
        const val POSITION_LENGTH = 40
        const val MAX_WAYPOINTS = 500
    }
}
