package io.github.noxaur.dsmod.core.waypoint

import io.github.noxaur.dsmod.core.text.number
import kotlin.math.abs
import kotlin.math.floor

/** @keep A shared waypoint is plain text that a player without the mod can read, as `[Nest] x: 12, y: 64, z: -30 in The End`.
 * The name and the area are left out when there are none. Other mods send `x: 12, y: 64, z: -30`, so that is found too, with
 * or without commas and with decimals, which round down to the block. Three bare numbers are not, because chat is full of
 * them. The area is read only when the line ends with it, so words a player types after the coordinates are not taken for
 * one */
object WaypointText {
    data class Found(val range: IntRange, val waypoint: SavedWaypoint)

    private const val NUMBER = """(-?\d{1,8}(?:\.\d+)?)"""

    /** @keep The server starts and ends an area's name with a capital word, as `The Catacombs (F1)` or `Isle of Dragons`, and
     * players type theirs in lowercase, so `in the cave` is no area */
    private const val WORD = """[A-Z0-9(][\w'()-]*"""
    private const val AREA = """$WORD(?:(?: [\w'()-]+)* $WORD)?"""

    private val SHARED = Regex(
        """(?:\[([^\[\]]{1,${SavedWaypoint.NAME_LENGTH}})] )?\b[xX]:? ?$NUMBER,? [yY]:? ?$NUMBER,? [zZ]:? ?$NUMBER(?!\d)(?: in ($AREA)$)?""",
    )

    private val TYPED = Regex("""(?:[xX]:? ?)?$NUMBER[, ]+(?:[yY]:? ?)?$NUMBER[, ]+(?:[zZ]:? ?)?$NUMBER""")

    // @keep The world border and the build limits, so a number that cannot be a block is no position.
    private const val FAR = 30_000_000
    private const val LOW = -2048
    private const val HIGH = 4096

    fun format(waypoint: SavedWaypoint): String = buildString {
        val name = clean(waypoint.name)
        if (name.isNotEmpty()) append("[").append(name).append("] ")
        append("x: ").append(waypoint.x).append(", y: ").append(waypoint.y).append(", z: ").append(waypoint.z)
        val area = clean(waypoint.area)
        if (area.isNotEmpty()) append(" in ").append(area)
    }

    fun find(text: String): List<Found> = SHARED.findAll(text).mapNotNull { match ->
        val (name, x, y, z, area) = match.destructured
        block(x, y, z)?.let { (bx, by, bz) -> Found(match.range, SavedWaypoint(clean(name), bx, by, bz, clean(area).take(SavedWaypoint.AREA_LENGTH))) }
    }.toList()

    fun position(typed: String): Triple<Int, Int, Int>? {
        val (x, y, z) = TYPED.matchEntire(typed.trim())?.destructured ?: return null
        return block(x, y, z)
    }

    private fun block(x: String, y: String, z: String): Triple<Int, Int, Int>? {
        val bx = floor(number(x) ?: return null).toInt()
        val by = floor(number(y) ?: return null).toInt()
        val bz = floor(number(z) ?: return null).toInt()
        if (abs(bx) > FAR || abs(bz) > FAR || by < LOW || by > HIGH) return null
        return Triple(bx, by, bz)
    }

    fun clean(text: String) = text.filterNot { it == '[' || it == ']' || it == '|' || it == '"' || it == '\\' || it.isISOControl() }.trim()
}
