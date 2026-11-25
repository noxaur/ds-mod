package io.github.noxaur.dragen.core.waypoint

import io.github.noxaur.dragen.core.config.Bind
import io.github.noxaur.dragen.core.config.Cell
import io.github.noxaur.dragen.core.config.Grouped
import io.github.noxaur.dragen.core.config.Items
import io.github.noxaur.dragen.core.config.Share
import io.github.noxaur.dragen.core.world.WaypointLook
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WaypointKindTest {
    private val kind = WaypointKind { group -> SavedWaypoint(x = 7, y = 8, z = 9, group = group) }

    private fun list(vararg waypoints: SavedWaypoint): Items<SavedWaypoint> {
        var value = Grouped(waypoints.toList())
        return Items("waypoints", "Waypoints", "", Bind("General.waypoints", { value }, { value = it }), kind)
    }

    @Test
    fun `an exported group imports as the same waypoints`() {
        val nest = SavedWaypoint("Nest", 12, 64, -30, "The End", 0x46AADCF8, WaypointLook.OUTLINE, distance = false, throughWalls = false)
        val from = list(nest, SavedWaypoint("Off", 1, 2, 3, enabled = false))
        val text = checkNotNull(from.export(""))
        val to = list()
        assertEquals("Imported" to 2, to.import(text))
        assertEquals(listOf(nest.copy(group = "Imported"), SavedWaypoint("Off", 1, 2, 3, enabled = false, group = "Imported")), to.value.items)
    }

    @Test
    fun `a big group exported imports whole`() {
        val many = List(1000) { SavedWaypoint("Waypoint number $it", it, 64, -it, "The End", 0x46AADCF8) }
        val to = list()
        assertEquals("Imported" to 1000, to.import(checkNotNull(list(*many.toTypedArray()).export(""))))
    }

    @Test
    fun `an import cleans each waypoint and drops the ones with no block`() {
        val bad = mapOf(
            "group" to "Shared",
            "waypoints" to listOf(
                mapOf("name" to "[x".repeat(40), "x" to 1, "y" to 2, "z" to 3, "area" to "a|b"),
                mapOf("name" to "Far", "x" to 99_999_999, "y" to 2, "z" to 3),
                mapOf("x" to 4, "y" to 5, "z" to 6, "look" to "NOPE"),
            ),
        )
        val (group, waypoints) = checkNotNull(kind.decode(Share.encode("dragen-waypoints:", bad)))
        assertEquals("Shared", group)
        assertEquals(listOf("x".repeat(32), ""), waypoints.map { it.name })
        assertEquals("ab", waypoints[0].area)
        assertEquals(WaypointLook.BEAM, waypoints[1].look)
    }

    @Test
    fun `other text on the clipboard imports nothing`() {
        assertNull(kind.decode("dragen-binds:H4sIAAAAAAAA"))
        assertNull(kind.decode("hello"))
    }

    @Test
    fun `typing a position moves the waypoint once it parses, and keeps it until then`() {
        val position = kind.cells.filterIsInstance<Cell.Text<SavedWaypoint>>().single { it.name == "Position" }
        val start = SavedWaypoint("Nest", 1, 2, 3)
        assertEquals(start, position.set(start, "12 6"))
        assertEquals(start.copy(x = 12, y = 64, z = -30), position.set(start, "12 64 -30"))
    }

    @Test
    fun `a new waypoint opens its name for typing`() {
        assertEquals("Name", kind.opens)
        assertEquals(SavedWaypoint(x = 7, y = 8, z = 9, group = "Mining"), kind.blank("Mining"))
    }
}
