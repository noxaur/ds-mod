package io.github.noxaur.dsmod.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class TypeAheadTest {
    private val labels = listOf("None", "Flat", "Rounded", "Fill", "Frame", "Glow")

    @Test
    fun `a letter lights the next item that starts with it, ignoring case`() {
        val keys = TypeAhead()
        assertEquals(1, keys.type('f', 0, labels, 0))
        assertEquals(2, TypeAhead().type('R', 0, labels, 0))
    }

    @Test
    fun `the same letter again cycles through the items that start with it, round to the first`() {
        val keys = TypeAhead()
        var lit = keys.type('f', 0, labels, 0)
        lit = keys.type('f', 200, labels, lit)
        assertEquals(3, lit)
        lit = keys.type('f', 400, labels, lit)
        assertEquals(4, lit)
        lit = keys.type('f', 600, labels, lit)
        assertEquals(1, lit, "past the last one it starts again from the top")
    }

    @Test
    fun `letters typed quickly match the start of a label together, and the lit item stays while it matches`() {
        val keys = TypeAhead()
        var lit = keys.type('f', 0, labels, 0)
        lit = keys.type('r', 300, labels, lit)
        assertEquals(4, lit, "fr finds Frame past Flat and Fill")
        val again = TypeAhead()
        lit = again.type('f', 0, labels, 0)
        lit = again.type('l', 300, labels, lit)
        assertEquals(1, lit, "fl stays on Flat, which it already lit")
    }

    @Test
    fun `a pause starts a new prefix`() {
        val keys = TypeAhead()
        var lit = keys.type('f', 0, labels, 0)
        lit = keys.type('g', 0 + TypeAhead.WINDOW + 1, labels, lit)
        assertEquals(5, lit, "after a pause, g is a new search rather than fg")
    }

    @Test
    fun `no match keeps the lit item`() {
        val keys = TypeAhead()
        assertEquals(2, keys.type('z', 0, labels, 2))
        assertEquals(2, keys.type('q', 100, labels, 2))
    }
}
