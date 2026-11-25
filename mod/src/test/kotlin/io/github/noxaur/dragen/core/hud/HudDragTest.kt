package io.github.noxaur.dragen.core.hud

import kotlin.test.Test
import kotlin.test.assertEquals

class HudDragTest {
    private val block = HudBox("timer", 50f, 50f, 40f, 20f)
    private val other = HudBox("tracker", 200f, 100f, 60f, 30f)

    private fun drag(to: Pair<Float, Float>, free: Boolean = false, others: List<HudBox> = listOf(other)) =
        HudDrag(block, 60f, 55f).to(block, to.first, to.second, others, 320f, 240f, free).let { it.x to it.y }

    @Test
    fun `the block moves from where it was grabbed`() {
        assertEquals(90f to 65f, drag(100f to 70f))
    }

    @Test
    fun `the block lines up with the screen and the other blocks`() {
        assertEquals(2f to 65f, drag(14f to 70f), "its left on the screen's margin")
        assertEquals(140f to 65f, drag(147f to 70f), "its middle on the screen's middle")
        assertEquals(160f to 65f, drag(168f to 70f), "its right on the other block's left")
        assertEquals(90f to 100f, drag(100f to 103f), "its top on the other block's top")
    }

    @Test
    fun `free skips the lining up`() {
        assertEquals(4f to 65f, drag(14f to 70f, free = true))
    }

    @Test
    fun `the block itself is not a line to line up with`() {
        assertEquals(43f to 65f, drag(53f to 70f, others = listOf(block)))
    }

    @Test
    fun `the block stays on the screen`() {
        assertEquals(0f to 0f, drag(-40f to -40f, free = true))
        assertEquals(280f to 220f, drag(400f to 400f, free = true))
    }

    private val dock = HudBox("left", 100f, 200f, 40f, 20f)

    private fun drop(to: Pair<Float, Float>, free: Boolean = false) =
        HudDrag(block, 60f, 55f).to(block, to.first, to.second, listOf(other), 320f, 240f, free, mapOf(HudDock.LEFT_OF_HOTBAR to dock))

    @Test
    fun `a block near a dock spot docks there`() {
        assertEquals(HudDrop(100f, 200f, HudDock.LEFT_OF_HOTBAR), drop(117f to 198f), "7 and 3 away")
        assertEquals(HudDock.FREE, drop(119f to 198f).dock, "9 away across")
        assertEquals(HudDock.FREE, drop(117f to 198f, free = true).dock, "with Shift")
    }

    @Test
    fun `a nudge moves the block off the mouse and keeps it there`() {
        val drag = HudDrag(block, 60f, 55f)
        drag.nudge(1f, -10f)
        val to = drag.to(block, 100f, 70f, emptyList(), 320f, 240f, free = true)
        assertEquals(91f to 55f, to.x to to.y)
    }

    @Test
    fun `an arrow nudges a block and keeps it on the screen`() {
        fun assertNudged(expected: HudDrop, from: HudBox, dx: Float, dy: Float) = assertEquals(expected, HudDrag.nudged(from, dx, dy, 320f, 240f))
        assertNudged(HudDrop(51f, 40f), block, 1f, -10f)
        assertNudged(HudDrop(0f, 50f), block.copy(x = 0f), -1f, 0f)
        assertNudged(HudDrop(280f, 50f), block.copy(x = 275f), 10f, 0f)
    }
}
