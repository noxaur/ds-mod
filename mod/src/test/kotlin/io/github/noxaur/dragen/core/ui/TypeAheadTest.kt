package io.github.noxaur.dragen.core.ui

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

    private fun typed(vararg presses: Pair<Char, Long>): List<Int> {
        val keys = TypeAhead()
        var lit = 0
        return presses.map { (char, at) -> keys.type(char, at, labels, lit).also { lit = it } }
    }

    @Test
    fun `the same letter again cycles through the items that start with it, round to the first`() {
        assertEquals(listOf(1, 3, 4, 1), typed('f' to 0L, 'f' to 200L, 'f' to 400L, 'f' to 600L), "past the last one it starts again from the top")
    }

    @Test
    fun `letters typed quickly match the start of a label together, and the lit item stays while it matches`() {
        assertEquals(4, typed('f' to 0L, 'r' to 300L).last(), "fr finds Frame past Flat and Fill")
        assertEquals(1, typed('f' to 0L, 'l' to 300L).last(), "fl stays on Flat, which it already lit")
    }

    @Test
    fun `a pause starts a new prefix`() {
        assertEquals(5, typed('f' to 0L, 'g' to TypeAhead.WINDOW + 1).last(), "after a pause, g is a new search rather than fg")
    }

    @Test
    fun `no match keeps the lit item`() {
        val keys = TypeAhead()
        assertEquals(2, keys.type('z', 0, labels, 2))
        assertEquals(2, keys.type('q', 100, labels, 2))
    }
}
