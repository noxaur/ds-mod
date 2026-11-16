package io.github.noxaur.dsmod.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavTreeTest {
    /** Three sections: one with three subsections, one with none, one with two. */
    private fun tree() = NavTree(listOf(3, 0, 2))

    private fun NavTree.lines() = rows.joinToString(" ") { if (it.isSection) "${it.section}" else "${it.section}.${it.sub}" }

    @Test
    fun `the selected section shows its subsections and the others do not`() {
        val tree = tree()
        assertEquals("0 0.0 0.1 0.2 1 2", tree.lines())
        tree.select(2)
        assertEquals("0 1 2 2.0 2.1", tree.lines())
        assertFalse(tree.shows(1), "a section without subsections has nothing to show")
    }

    @Test
    fun `an opened section stays open across changes of section`() {
        val tree = tree()
        tree.toggle(2)
        tree.select(1)
        assertEquals("0 1 2 2.0 2.1", tree.lines(), "the selected one closes when left, the opened one stays")
        tree.select(0)
        assertEquals("0 0.0 0.1 0.2 1 2 2.0 2.1", tree.lines())
    }

    @Test
    fun `closing the selected section holds until it is selected again`() {
        val tree = tree()
        tree.toggle(0)
        assertEquals("0 1 2", tree.lines())
        tree.select(2)
        tree.select(0)
        assertEquals("0 0.0 0.1 0.2 1 2", tree.lines(), "selecting clears the closed mark")
    }

    @Test
    fun `a closed section that is opened again shows after it is left`() {
        val tree = tree()
        tree.toggle(0)
        tree.toggle(0)
        tree.select(1)
        assertEquals("0 0.0 0.1 0.2 1 2", tree.lines())
    }

    @Test
    fun `collapse all closes every section, the selected one too, and expand all opens them`() {
        val tree = tree()
        assertTrue(tree.anyShown)
        tree.collapseAll()
        assertEquals("0 1 2", tree.lines())
        assertFalse(tree.anyShown)
        tree.expandAll()
        assertEquals("0 0.0 0.1 0.2 1 2 2.0 2.1", tree.lines())
        tree.collapseAll()
        tree.select(2)
        assertEquals("0 1 2 2.0 2.1", tree.lines(), "selecting after collapse all still opens the new section")
    }

    @Test
    fun `a jump selects the section and marks the subsection`() {
        val tree = tree()
        tree.toggle(2)
        tree.jump(2, 1)
        assertEquals(2, tree.selected)
        assertTrue(tree.marked(NavRow(2, 1)))
        assertFalse(tree.marked(NavRow(2, 0)))
        assertFalse(tree.marked(NavRow(0, 1)), "only the selected section's subsections are marked")
        assertEquals(NavRow(2, 1), tree.cursor)
        tree.select(0)
        assertTrue(tree.marked(NavRow(0, 0)), "selecting a section starts at its first subsection")
    }

    @Test
    fun `a blank tab selects no section, so none shows its subsections or is marked`() {
        val tree = tree()
        tree.deselect()
        assertEquals("0 1 2", tree.lines())
        assertFalse(tree.marked(NavRow(0)))
        assertFalse(tree.marked(NavRow(0, 0)))
        tree.select(2)
        assertEquals("0 1 2 2.0 2.1", tree.lines())
    }

    @Test
    fun `a search hides every subsection and marks nothing`() {
        val tree = tree()
        tree.expandAll()
        tree.searching = true
        assertEquals("0 1 2", tree.lines())
        assertFalse(tree.anyShown)
        assertFalse(tree.marked(NavRow(0)))
        tree.searching = false
        assertEquals("0 0.0 0.1 0.2 1 2 2.0 2.1", tree.lines(), "the open sections come back")
    }

    @Test
    fun `up and down move through the lines shown, and stop at the ends`() {
        val tree = tree()
        tree.move(-1)
        assertEquals(NavRow(0), tree.cursor)
        tree.move(2)
        assertEquals(NavRow(0, 1), tree.cursor)
        tree.move(10)
        assertEquals(NavRow(2), tree.cursor)
    }

    @Test
    fun `right opens a section and then steps into it, and left steps out and then closes it`() {
        val tree = tree()
        tree.moveTo(NavRow(2))
        tree.right()
        assertEquals("0 0.0 0.1 0.2 1 2 2.0 2.1", tree.lines())
        assertEquals(NavRow(2), tree.cursor)
        tree.right()
        assertEquals(NavRow(2, 0), tree.cursor)
        tree.left()
        assertEquals(NavRow(2), tree.cursor)
        tree.left()
        assertEquals("0 0.0 0.1 0.2 1 2", tree.lines())
        tree.moveTo(NavRow(1))
        tree.right()
        assertEquals(NavRow(1), tree.cursor, "a section without subsections does not open")
    }

    @Test
    fun `a cursor on a subsection that closes moves to its section`() {
        val tree = tree()
        tree.moveTo(NavRow(0, 2))
        tree.collapseAll()
        assertEquals(NavRow(0), tree.cursor)
    }

    @Test
    fun `the current subsection is the last heading near the pane's top, or the last at the end`() {
        val tops = intArrayOf(36, 120, 200)
        assertEquals(0, NavTree.current(tops, 0, 400))
        assertEquals(0, NavTree.current(tops, 93, 400), "27 below the top is too far")
        assertEquals(1, NavTree.current(tops, 94, 400), "26 below the top counts")
        assertEquals(1, NavTree.current(tops, 150, 400))
        assertEquals(2, NavTree.current(tops, 180, 400))
        assertEquals(2, NavTree.current(intArrayOf(36, 120, 380), 400, 400), "a heading that can't reach the top is current at the end")
        assertEquals(0, NavTree.current(IntArray(0), 0, 0))
        assertEquals(0, NavTree.current(tops, 0, 0), "a pane with no scroll is at its start, not its end")
    }

    @Test
    fun `the narrator hears a line's kind, whether it shows its subsections, and the mark`() {
        val tree = tree()
        val names = listOf("The End", "Combat", "Camera")
        val subs = listOf(listOf("Dragon", "Protector", "Drops"), emptyList(), listOf("Free Look", "F5 Views"))
        assertEquals("The End, section, expanded, selected", tree.narration(NavRow(0), names, subs, null))
        assertEquals("Combat, section", tree.narration(NavRow(1), names, subs, null))
        assertEquals("Camera, section, collapsed", tree.narration(NavRow(2), names, subs, null))
        assertEquals("Dragon, subsection of The End, current", tree.narration(NavRow(0, 0), names, subs, null))
        assertEquals("Drops, subsection of The End", tree.narration(NavRow(0, 2), names, subs, null))
        tree.searching = true
        val counts = intArrayOf(3, 0, 1)
        assertEquals("The End, section, 3 matches", tree.narration(NavRow(0), names, subs, counts))
        assertEquals("Combat, section, no matches", tree.narration(NavRow(1), names, subs, counts))
        assertEquals("Camera, section, 1 match", tree.narration(NavRow(2), names, subs, counts))
    }
}
