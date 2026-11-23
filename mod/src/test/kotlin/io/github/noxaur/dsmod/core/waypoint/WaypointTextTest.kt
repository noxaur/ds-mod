package io.github.noxaur.dsmod.core.waypoint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WaypointTextTest {
    private fun found(text: String) = WaypointText.find(text).map { it.waypoint }

    @Test
    fun `a shared waypoint reads back as the waypoint it was made from`() {
        val nest = SavedWaypoint("Nest", 12, 64, -30, "The End")
        val line = WaypointText.format(nest)
        assertEquals("[Nest] x: 12, y: 64, z: -30 in The End", line)
        assertEquals(listOf(nest), found("Party > [DRAGON] Bob: $line"))
    }

    @Test
    fun `a waypoint with no name or area is only its coordinates`() {
        val line = WaypointText.format(SavedWaypoint(x = -5, y = 70, z = 8))
        assertEquals("x: -5, y: 70, z: 8", line)
        assertEquals(listOf(SavedWaypoint(x = -5, y = 70, z = 8)), found("Bob: $line"))
    }

    @Test
    fun `the range found covers the name and the area, so the whole share is one link`() {
        val text = "Bob: [Nest] x: 1, y: 2, z: 3 in The End"
        assertEquals("[Nest] x: 1, y: 2, z: 3 in The End", text.substring(WaypointText.find(text).single().range))
    }

    @Test
    fun `other mods' coordinates are found with or without commas, and decimals round down to the block`() {
        val blocks = mapOf("x: 1, y: 2, z: 3" to Triple(1, 2, 3), "X:1 Y:2 Z:3" to Triple(1, 2, 3), "x: -12.7, y: 64.2, z: 4.9" to Triple(-13, 64, 4))
        for ((text, block) in blocks) assertEquals(listOf(block), found(text).map { Triple(it.x, it.y, it.z) }, text)
    }

    @Test
    fun `three bare numbers are no waypoint, because chat is full of them`() {
        assertEquals(emptyList(), found("Bob: 1 2 3 4 5"))
        assertEquals(emptyList(), found("Bob: sold 12, 64, 30"))
    }

    @Test
    fun `words after the coordinates are not taken for an area`() {
        assertEquals(listOf(SavedWaypoint(x = 1, y = 2, z = 3)), found("Bob: x: 1, y: 2, z: 3 in the cave, come quick"))
        assertEquals(listOf(SavedWaypoint(x = 1, y = 2, z = 3)), found("Bob: x: 1, y: 2, z: 3 in the cave"))
        assertEquals("The Catacombs (F1)", found("x: 1, y: 2, z: 3 in The Catacombs (F1)").single().area)
        assertEquals("Dragon's Den", found("x: 1, y: 2, z: 3 in Dragon's Den").single().area)
        val isle = SavedWaypoint(x = 1, y = 2, z = 3, area = "Isle of Dragons")
        assertEquals(listOf(isle), found(WaypointText.format(isle)))
        assertEquals("", found("x: 1, y: 2, z: 3 in The End of it").single().area)
    }

    @Test
    fun `coordinates past the world border or the build limits are no waypoint`() {
        assertEquals(emptyList(), found("x: 99999999, y: 64, z: 0"))
        assertEquals(emptyList(), found("x: 0, y: 9000, z: 0"))
        assertEquals(emptyList(), found("x: 1, y: 2, z: 100000000"), "a number too long is not cut to one that fits")
    }

    @Test
    fun `a share at the end of a sentence is found`() {
        assertEquals(listOf(SavedWaypoint(x = 1, y = 2, z = 3)), found("meet at x: 1, y: 2, z: 3."))
    }

    @Test
    fun `two waypoints in one line are both found`() {
        assertEquals(2, WaypointText.find("x: 1, y: 2, z: 3 or x: 4, y: 5, z: 6").size)
    }

    @Test
    fun `a typed position takes spaces, commas or the labels`() {
        assertEquals(Triple(12, 64, -30), WaypointText.position("12 64 -30"))
        assertEquals(Triple(12, 64, -30), WaypointText.position(" 12, 64, -30 "))
        assertEquals(Triple(12, 64, -30), WaypointText.position("x: 12, y: 64, z: -30"))
        assertNull(WaypointText.position("12 64"))
        assertNull(WaypointText.position("12 64 -"))
    }

    @Test
    fun `a name or area loses the characters that would break the share or the click`() {
        assertEquals("Bob s nest", WaypointText.clean("[Bob| s \"nest\\]"))
        assertEquals("x: 1, y: 2, z: 3", WaypointText.format(SavedWaypoint("[]", 1, 2, 3, "|")))
    }
}
