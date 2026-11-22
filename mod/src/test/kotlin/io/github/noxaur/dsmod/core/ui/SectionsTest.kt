package io.github.noxaur.dsmod.core.ui

import io.github.noxaur.dsmod.core.ui.Sections.Edge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SectionsTest {
    private val two = listOf(Section(40, 80), Section(150, 200))

    private fun assertAdd(expected: List<Section>, at: Int, why: String? = null, to: List<Section> = two) = assertEquals(expected, Sections.add(to, at, 25, 200), why)

    private fun assertDraw(expected: List<Section>, from: Int, to: Int, why: String? = null, on: List<Section> = two) =
        assertEquals(expected, Sections.draw(on, from, to, 200), why)

    private fun assertDrawn(expected: Section, from: Int, to: Int, why: String? = null) = assertDraw(listOf(expected), from, to, why, emptyList())

    private fun assertResize(expected: List<Section>, index: Int, edge: Edge, to: Int, why: String? = null) =
        assertEquals(expected, Sections.resize(two, index, edge, to, 200), why)

    private fun assertMove(expected: List<Section>, index: Int, by: Int, why: String? = null) = assertEquals(expected, Sections.move(two, index, by, 200), why)

    private fun assertMerged(expected: List<Section>, vararg sections: Section, why: String? = null) = assertEquals(expected, Sections.merged(sections.toList()), why)

    @Test
    fun `a value is on inside a section and at its edges`() {
        listOf(40 to true, 80 to true, 200 to true, 39 to false, 100 to false).forEach { (value, on) ->
            assertEquals(on, Sections.contains(two, value), "contains $value")
        }
        assertEquals(1, Sections.at(two, 160))
        assertEquals(-1, Sections.at(two, 100))
    }

    @Test
    fun `a click adds a section around itself, cut to the room it has`() {
        assertAdd(listOf(Section(40, 80), Section(100, 125), Section(150, 200)), 112)
        assertAdd(listOf(Section(0, 25), Section(40, 80), Section(150, 200)), 3, "pushed in from the scale's start")
        assertAdd(listOf(Section(40, 80), Section(84, 96), Section(100, 200)), 90, "kept clear of both neighbors", listOf(Section(40, 80), Section(100, 200)))
    }

    @Test
    fun `a click with no room or on a section adds nothing`() {
        val tight = listOf(Section(40, 80), Section(90, 200))
        assertAdd(tight, 85, to = tight)
        assertAdd(two, 60)
    }

    @Test
    fun `a drag draws a section between where it began and where the mouse is`() {
        val drawn = listOf(Section(40, 80), Section(95, 130), Section(150, 200))
        assertDraw(drawn, 95, 130)
        assertDraw(drawn, 130, 95, "drawn backwards")
        assertDrawn(Section(100, 105), 100, 101, "never too short to grab")
        assertDrawn(Section(95, 100), 100, 99, "and it grows the way the mouse goes")
        assertDrawn(Section(195, 200), 199, 200)
        assertDrawn(Section(0, 5), 1, 0)
        assertDrawn(Section(180, 200), 180, 999, "stopped at the scale's end")
    }

    @Test
    fun `a drawn section joins the one it ends in or right beside`() {
        assertDraw(listOf(Section(40, 80), Section(100, 200)), 100, 170, "released inside the next one")
        assertDraw(listOf(Section(40, 80), Section(100, 200)), 100, 147, "released three short of it")
        assertDraw(listOf(Section(40, 80), Section(100, 146), Section(150, 200)), 100, 146, "four short stays apart")
        assertDraw(listOf(Section(40, 200)), 70, 160, "across the gap joins all three")
    }

    @Test
    fun `an edge dragged into or beside a neighbor joins the two`() {
        val joined = listOf(Section(40, 200))
        assertResize(joined, 0, Edge.END, 148)
        assertResize(joined, 0, Edge.END, 180)
        assertResize(joined, 1, Edge.START, 82)
        assertResize(listOf(Section(40, 140), Section(150, 200)), 0, Edge.END, 140, "ten short stays apart")
    }

    @Test
    fun `an edge stops at the scale's end and at its own other edge`() {
        assertResize(listOf(Section(40, 45), Section(150, 200)), 0, Edge.END, 10, "never shorter than five")
        assertResize(listOf(Section(0, 80), Section(150, 200)), 0, Edge.START, -30)
        assertResize(listOf(Section(40, 80), Section(150, 200)), 1, Edge.END, 999)
        assertResize(listOf(Section(40, 80), Section(195, 200)), 1, Edge.START, 999)
    }

    @Test
    fun `a section slides along the scale and joins a neighbor it reaches`() {
        assertMove(listOf(Section(90, 130), Section(150, 200)), 0, 90)
        assertMove(listOf(Section(0, 40), Section(150, 200)), 0, -9)
        assertMove(listOf(Section(108, 200)), 0, 108, "two short of the next one")
        assertMove(listOf(Section(40, 80), Section(150, 200)), 1, 999, "kept on the scale")
    }

    @Test
    fun `sections that overlap or nearly touch are one`() {
        assertMerged(listOf(Section(0, 30), Section(34, 60)), Section(34, 60), Section(0, 30))
        assertMerged(listOf(Section(0, 60)), Section(33, 60), Section(0, 30))
        assertMerged(listOf(Section(0, 90)), Section(0, 90), Section(20, 40), why = "one inside another")
        assertEquals(listOf(Section(150, 200)), Sections.remove(two, 0))
    }
}
