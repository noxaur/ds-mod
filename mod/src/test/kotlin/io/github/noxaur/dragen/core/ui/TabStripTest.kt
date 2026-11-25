package io.github.noxaur.dragen.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabStripTest {
    private fun strip(count: Int, room: Int, selected: Int = 0) = TabStrip().also { it.layout(count, room, selected) }

    @Test
    fun `tabs are at their widest while they fit, with the new tab button after the last`() {
        val strip = strip(2, 300)
        assertEquals(TabStrip.MAX, strip.width)
        assertEquals(93, strip.x(1))
        assertEquals(2 * 93, strip.newX)
        assertFalse(strip.overflows)
    }

    @Test
    fun `tabs shrink to share the room, down to the narrowest`() {
        val strip = strip(4, 260)
        assertEquals(60, strip.width)
        assertFalse(strip.overflows)
        assertEquals(4 * 61, strip.newX)
        assertTrue(strip.newX + TabStrip.NEW <= 260)
        assertEquals(TabStrip.MIN, strip(9, 260).width)
    }

    @Test
    fun `past the narrowest the strip scrolls, keeps the selected tab in sight, and pins the button after it`() {
        val strip = strip(9, 260, selected = 8)
        assertTrue(strip.overflows)
        assertEquals(244, strip.view)
        assertEquals(245, strip.newX, "the button sits after the view")
        val right = strip.x(8) + strip.width
        assertEquals(strip.view, right, "the last tab ends at the view's edge")
        strip.reveal(0)
        assertEquals(0, strip.x(0))
        strip.scroll(-50)
        assertEquals(0, strip.offset, "it stops at the start")
        strip.scroll(10_000)
        assertEquals(strip.view, strip.x(8) + strip.width, "and at the end")
    }

    @Test
    fun `fewer tabs pull a scrolled strip back`() {
        val strip = strip(9, 260, selected = 8)
        strip.layout(3, 260, 2)
        assertEquals(0, strip.offset)
    }

    @Test
    fun `a press lands on the tab under it, and not in the gap between two`() {
        val strip = strip(3, 300)
        assertEquals(0, strip.at(0))
        assertEquals(0, strip.at(91))
        assertEquals(-1, strip.at(92), "the 1px gap")
        assertEquals(1, strip.at(93))
        assertEquals(-1, strip.at(3 * 93), "past the last tab")
        assertEquals(-1, strip.at(-1))
    }

    @Test
    fun `a scrolled strip finds the tab under the press with its offset`() {
        val strip = strip(9, 260, selected = 8)
        val x = strip.x(8)
        assertEquals(8, strip.at(x + 1))
        assertEquals(-1, strip.at(strip.view), "nothing past the view")
    }

    @Test
    fun `a dragged tab passes a neighbor once the mouse crosses the neighbor's middle`() {
        val strip = strip(4, 400)
        assertEquals(0, strip.drop(138, 0))
        assertEquals(1, strip.drop(139, 0))
        assertEquals(3, strip.drop(399, 0), "past the last tab it goes last")
        assertEquals(3, strip.drop(3 * 93 + 46, 2))
        assertEquals(2, strip.drop(2 * 93 + 45, 3), "back over tab 2's middle")
        assertEquals(0, strip.drop(-20, 3))
    }

    @Test
    fun `a dragged tab follows the mouse but stays within the strip`() {
        val strip = strip(3, 400)
        assertEquals(50, strip.dragX(60, 10))
        assertEquals(0, strip.dragX(5, 10))
        assertEquals(2 * 93, strip.dragX(1000, 10))
    }
}
