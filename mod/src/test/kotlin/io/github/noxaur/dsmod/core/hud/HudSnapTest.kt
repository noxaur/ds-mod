package io.github.noxaur.dsmod.core.hud

import kotlin.test.Test
import kotlin.test.assertEquals

class HudSnapTest {
    private fun snap(at: Float) = HudSnap.snap(at, 40f, edges = listOf(2f, 318f, 100f), centers = listOf(160f), reach = 5f)

    @Test
    fun `a block takes the nearest line within reach`() {
        assertEquals(2f, snap(5f), "its start on the screen's edge")
        assertEquals(278f, snap(281f), "its end on the far edge")
        assertEquals(140f, snap(137f), "its middle on the center line")
        assertEquals(100f, snap(98f), "its start on another block's edge")
        assertEquals(60f, snap(63f), "its end on another block's edge")
    }

    @Test
    fun `a block out of reach stays where it was dragged`() {
        assertEquals(30f, snap(30f))
        assertEquals(146f, snap(146f))
    }

    @Test
    fun `the nearer line wins`() {
        assertEquals(100f, HudSnap.snap(101f, 40f, edges = listOf(100f, 104f), centers = emptyList(), reach = 5f))
    }

    @Test
    fun `a dropped block lines up, unless Shift is held, and always stays whole on the screen`() {
        val other = HudBox(100f, 50f, 40f, 20f)
        assertEquals(100f to 120f, HudSnap.place(97f, 120f, 30f, 10f, listOf(other), 320f, 180f, free = false), "its start takes the other block's edge")
        assertEquals(97f to 120f, HudSnap.place(97f, 120f, 30f, 10f, listOf(other), 320f, 180f, free = true), "Shift leaves it where it was dragged")
        assertEquals(290f to 170f, HudSnap.place(400f, 400f, 30f, 10f, emptyList(), 320f, 180f, free = true), "past the corner it stops with its far edges on the screen's")
        assertEquals(0f to 0f, HudSnap.place(-50f, -9f, 30f, 10f, emptyList(), 320f, 180f, free = true))
    }

    @Test
    fun `the map keeps the screen's shape and turns its points back into the screen's`() {
        val map = HudMap(640, 360, 320, 400)
        assertEquals(0.5f, map.scale)
        assertEquals(320 to 180, map.width to map.height)
        assertEquals(200f to 100f, map.screen(100.0) to map.screen(50.0))
        assertEquals(160 to 90, HudMap(640, 360, 320, 90).let { it.width to it.height }, "a short room takes the height first")
    }
}
