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
}
