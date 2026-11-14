package io.github.noxaur.dsmod.core.ui

import io.github.noxaur.dsmod.core.ui.Sections.Edge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SectionsTest {
    private val two = listOf(Section(40, 80), Section(150, 200))

    @Test
    fun `a value is on inside a section and at its edges`() {
        assertTrue(Sections.contains(two, 40))
        assertTrue(Sections.contains(two, 80))
        assertTrue(Sections.contains(two, 200))
        assertFalse(Sections.contains(two, 39))
        assertFalse(Sections.contains(two, 100))
        assertEquals(90, Sections.covered(two))
    }

    @Test
    fun `a new section sits around the click and stays sorted`() {
        assertEquals(listOf(Section(40, 80), Section(100, 125), Section(150, 200)), Sections.add(two, 112, 25, 200))
        assertEquals(listOf(Section(0, 25), Section(40, 80), Section(150, 200)), Sections.add(two, 3, 25, 200), "pushed in from the scale's start")
    }

    @Test
    fun `a new section is cut to the room its neighbors leave`() {
        val near = listOf(Section(40, 80), Section(100, 200))
        assertEquals(listOf(Section(40, 80), Section(85, 95), Section(100, 200)), Sections.add(near, 90, 25, 200))
    }

    @Test
    fun `a click with no room or on a section adds nothing`() {
        val tight = listOf(Section(40, 80), Section(88, 200))
        assertEquals(tight, Sections.add(tight, 84, 25, 200))
        assertEquals(two, Sections.add(two, 60, 25, 200))
    }

    @Test
    fun `an edge stops at its neighbor, the scale's end and its own other edge`() {
        assertEquals(Section(40, 145), Sections.resize(two, 0, Edge.END, 190, 200)[0], "five short of the next section")
        assertEquals(Section(40, 45), Sections.resize(two, 0, Edge.END, 10, 200)[0], "never shorter than five")
        assertEquals(Section(0, 80), Sections.resize(two, 0, Edge.START, -30, 200)[0])
        assertEquals(Section(85, 200), Sections.resize(two, 1, Edge.START, 0, 200)[1])
        assertEquals(Section(150, 200), Sections.resize(two, 1, Edge.END, 999, 200)[1])
    }

    @Test
    fun `a section slides between its neighbors and keeps its length`() {
        assertEquals(Section(105, 145), Sections.move(two, 0, 130, 200)[0])
        assertEquals(Section(0, 40), Sections.move(two, 0, -9, 200)[0])
        assertEquals(Section(85, 135), Sections.move(two, 1, 0, 200)[1])
    }

    @Test
    fun `removing a section leaves the rest`() {
        assertEquals(listOf(Section(150, 200)), Sections.remove(two, 0))
        assertEquals(1, Sections.at(two, 160))
        assertEquals(-1, Sections.at(two, 100))
    }
}
