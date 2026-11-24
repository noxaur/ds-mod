package io.github.noxaur.dsmod.core.feature.utilities.waypoints

import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.waypoint.SavedWaypoint
import io.github.noxaur.dsmod.core.world.Point
import io.github.noxaur.dsmod.core.world.Waypoint
import io.github.noxaur.dsmod.core.world.WaypointLook
import io.github.noxaur.dsmod.harness.Harness
import io.github.noxaur.dsmod.harness.text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WaypointsTest : Harness(alone = Waypoints::class) {
    private val waypoints = tree.feature<Waypoints>()
    private var at: Triple<Int, Int, Int>? = Triple(0, 64, 0)

    init {
        waypoints.position = { at }
    }

    private fun inArea(name: String) = post(TabList(listOf(text("Area: $name"))))

    private fun marks() = readout(waypoints).marks.map { (it as Waypoint).label }

    @Test
    fun `a saved waypoint shows only in the area it was made in`() {
        inArea("The End")
        assertTrue(waypoints.save(waypoints.here("Nest")!!))
        pass()
        assertEquals(listOf("Nest"), marks())
        inArea("Village")
        pass()
        assertEquals(emptyList(), marks())
        inArea("The End")
        pass()
        assertEquals(listOf("Nest"), marks())
    }

    @Test
    fun `a waypoint with no area shows everywhere`() {
        waypoints.save(SavedWaypoint("Spawn", 1, 2, 3))
        inArea("Village")
        pass()
        assertEquals(listOf("Spawn"), marks())
    }

    @Test
    fun `a waypoint that is off, or in a group that is off, does not show`() {
        waypoints.saved.value = listOf(SavedWaypoint("Off", enabled = false), SavedWaypoint("Grouped", group = "Mining"), SavedWaypoint("On"))
        waypoints.offGroups.value = listOf("Mining")
        pass()
        assertEquals(listOf("On"), marks())
    }

    @Test
    fun `a saved waypoint takes the default color and look, and draws with its own`() {
        inArea("The End")
        waypoints.look.value = WaypointLook.OUTLINE
        val nest = waypoints.here("Nest")!!
        assertEquals(WaypointLook.OUTLINE, nest.look)
        waypoints.save(nest.copy(color = 0x80FF0000.toInt(), distance = false, throughWalls = false))
        pass()
        val mark = readout(waypoints).marks.single() as Waypoint
        assertEquals(Waypoint(Point(0.5, 64.0, 0.5), "Nest", color = 0x80FF0000.toInt(), look = WaypointLook.OUTLINE, distance = false, throughWalls = false), mark)
    }

    @Test
    fun `saving the same waypoint twice keeps one`() {
        val nest = SavedWaypoint("Nest", 1, 2, 3, "The End")
        assertTrue(waypoints.save(nest))
        assertFalse(waypoints.save(nest))
        assertEquals(1, waypoints.saved.value.size)
    }

    @Test
    fun `a shown waypoint goes once the player walks up to it`() {
        at = Triple(100, 64, 100)
        waypoints.show(SavedWaypoint("Bob's", 10, 64, 10))
        pass()
        assertEquals(listOf("Bob's"), marks())
        at = Triple(13, 64, 12)
        pass()
        assertEquals(emptyList(), marks())
    }

    @Test
    fun `a shown waypoint far away, or in another area, stays`() {
        at = Triple(0, 64, 0)
        waypoints.show(SavedWaypoint("Far", 65536, 64, 0))
        waypoints.show(SavedWaypoint("Village", 1, 64, 0, "Village"))
        inArea("The End")
        pass()
        assertEquals(listOf("Far"), marks())
        at = Triple(500, 64, 500)
        inArea("Village")
        pass()
        assertEquals(listOf("Village", "Far"), marks(), "standing on its coordinates in another area left the Village waypoint be")
    }

    @Test
    fun `a shown waypoint goes with the world`() {
        at = Triple(100, 64, 100)
        waypoints.show(SavedWaypoint("Bob's", 10, 64, 10))
        post(WorldChanged)
        pass()
        assertEquals(emptyList(), marks())
    }

    @Test
    fun `only the last few shown waypoints show, and showing one again moves it to the front`() {
        at = Triple(1000, 64, 1000)
        for (i in 1..10) waypoints.show(SavedWaypoint("$i", i * 10, 64, 0))
        waypoints.show(SavedWaypoint("3 again", 30, 64, 0))
        pass()
        assertEquals(listOf("3 again", "10", "9", "8", "7", "6", "5", "4"), marks())
    }

    @Test
    fun `saving a shown waypoint keeps one copy`() {
        at = Triple(1000, 64, 1000)
        val bob = SavedWaypoint("Bob's", 10, 64, 10)
        waypoints.show(bob)
        waypoints.save(bob)
        pass()
        assertEquals(listOf("Bob's"), marks())
    }

    @Test
    fun `a chat waypoint takes the player's color and look, and their area when it names none`() {
        inArea("The End")
        waypoints.look.value = WaypointLook.LABEL
        val styled = waypoints.styled(SavedWaypoint("Bob's", 1, 2, 3))
        assertEquals(WaypointLook.LABEL, styled.look)
        assertEquals(waypoints.color.value, styled.color)
        assertEquals("The End", styled.area)
        assertEquals("Village", waypoints.styled(SavedWaypoint(area = "Village")).area)
    }

    @Test
    fun `a waypoint is shared by its name, preferring the one in the player's area`() {
        waypoints.saved.value = listOf(SavedWaypoint("Nest", 1, 1, 1, "Village"), SavedWaypoint("Nest", 2, 2, 2, "The End"))
        inArea("The End")
        assertEquals(2, waypoints.named("nest")?.x)
        waypoints.saved.value += SavedWaypoint("", 3, 3, 3)
        assertEquals(null, waypoints.named(" "), "no name shares where the player stands, not a waypoint with no name")
        assertEquals(listOf("Nest"), waypoints.names())
    }
}
