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
        assertEquals(1, Sections.at(two, 160))
        assertEquals(-1, Sections.at(two, 100))
    }

    @Test
    fun `a click adds a section around itself, cut to the room it has`() {
        assertEquals(listOf(Section(40, 80), Section(100, 125), Section(150, 200)), Sections.add(two, 112, 25, 200))
        assertEquals(listOf(Section(0, 25), Section(40, 80), Section(150, 200)), Sections.add(two, 3, 25, 200), "pushed in from the scale's start")
        val near = listOf(Section(40, 80), Section(100, 200))
        assertEquals(listOf(Section(40, 80), Section(84, 96), Section(100, 200)), Sections.add(near, 90, 25, 200), "kept clear of both neighbors")
    }

    @Test
    fun `a click with no room or on a section adds nothing`() {
        val tight = listOf(Section(40, 80), Section(90, 200))
        assertEquals(tight, Sections.add(tight, 85, 25, 200))
        assertEquals(two, Sections.add(two, 60, 25, 200))
    }

    @Test
    fun `a drag draws a section between where it began and where the mouse is`() {
        assertEquals(listOf(Section(40, 80), Section(95, 130), Section(150, 200)), Sections.draw(two, 95, 130, 200))
        assertEquals(listOf(Section(40, 80), Section(95, 130), Section(150, 200)), Sections.draw(two, 130, 95, 200), "drawn backwards")
        assertEquals(Section(100, 105), Sections.draw(emptyList(), 100, 101, 200).single(), "never too short to grab")
        assertEquals(Section(95, 100), Sections.draw(emptyList(), 100, 99, 200).single(), "and it grows the way the mouse goes")
        assertEquals(Section(195, 200), Sections.draw(emptyList(), 199, 200, 200).single())
        assertEquals(Section(0, 5), Sections.draw(emptyList(), 1, 0, 200).single())
        assertEquals(Section(180, 200), Sections.draw(emptyList(), 180, 999, 200).single(), "stopped at the scale's end")
    }

    @Test
    fun `a drawn section joins the one it ends in or right beside`() {
        assertEquals(listOf(Section(40, 80), Section(100, 200)), Sections.draw(two, 100, 170, 200), "released inside the next one")
        assertEquals(listOf(Section(40, 80), Section(100, 200)), Sections.draw(two, 100, 147, 200), "released three short of it")
        assertEquals(listOf(Section(40, 80), Section(100, 146), Section(150, 200)), Sections.draw(two, 100, 146, 200), "four short stays apart")
        assertEquals(listOf(Section(40, 200)), Sections.draw(two, 70, 160, 200), "across the gap joins all three")
    }

    @Test
    fun `an edge dragged into or beside a neighbor joins the two`() {
        assertEquals(listOf(Section(40, 200)), Sections.resize(two, 0, Edge.END, 148, 200))
        assertEquals(listOf(Section(40, 200)), Sections.resize(two, 0, Edge.END, 180, 200))
        assertEquals(listOf(Section(40, 200)), Sections.resize(two, 1, Edge.START, 82, 200))
        assertEquals(listOf(Section(40, 140), Section(150, 200)), Sections.resize(two, 0, Edge.END, 140, 200), "ten short stays apart")
    }

    @Test
    fun `an edge stops at the scale's end and at its own other edge`() {
        assertEquals(Section(40, 45), Sections.resize(two, 0, Edge.END, 10, 200)[0], "never shorter than five")
        assertEquals(Section(0, 80), Sections.resize(two, 0, Edge.START, -30, 200)[0])
        assertEquals(Section(150, 200), Sections.resize(two, 1, Edge.END, 999, 200)[1])
        assertEquals(Section(195, 200), Sections.resize(two, 1, Edge.START, 999, 200)[1])
    }

    @Test
    fun `a section slides along the scale and joins a neighbor it reaches`() {
        assertEquals(listOf(Section(90, 130), Section(150, 200)), Sections.move(two, 0, 90, 200))
        assertEquals(Section(0, 40), Sections.move(two, 0, -9, 200)[0])
        assertEquals(listOf(Section(108, 200)), Sections.move(two, 0, 108, 200), "two short of the next one")
        assertEquals(listOf(Section(40, 80), Section(150, 200)), Sections.move(two, 1, 999, 200), "kept on the scale")
    }

    @Test
    fun `sections that overlap or nearly touch are one`() {
        assertEquals(listOf(Section(0, 30), Section(34, 60)), Sections.merged(listOf(Section(34, 60), Section(0, 30))))
        assertEquals(listOf(Section(0, 60)), Sections.merged(listOf(Section(33, 60), Section(0, 30))))
        assertEquals(listOf(Section(0, 90)), Sections.merged(listOf(Section(0, 90), Section(20, 40))), "one inside another")
        assertEquals(listOf(Section(150, 200)), Sections.remove(two, 0))
    }
}
