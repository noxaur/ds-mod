package io.github.noxaur.dsmod.core.hud

import kotlin.test.Test
import kotlin.test.assertEquals

class HudDragTest {
    private val block = HudBox("timer", 50f, 50f, 40f, 20f)
    private val other = HudBox("tracker", 200f, 100f, 60f, 30f)

    // Grabbed 10 right and 5 down of its corner.
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
}
