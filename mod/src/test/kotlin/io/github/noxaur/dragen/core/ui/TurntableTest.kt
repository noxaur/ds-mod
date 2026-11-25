package io.github.noxaur.dragen.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TurntableTest {
    private val table = Turntable()

    @Test
    fun `it spins on its own`() {
        assertEquals(0f, table.yaw(0))
        assertEquals(Turntable.SPEED, table.yaw(1000))
    }

    @Test
    fun `a drag turns it, and on release it spins on from there`() {
        table.yaw(0)
        table.press(10.0, 10.0, 1000)
        table.drag(20.0, 15.0)
        assertEquals(Turntable.SPEED + 20f, table.yaw(5000))
        assertEquals(10f, table.pitch)
        table.release(5000)
        assertFalse(table.dragging)
        assertEquals(Turntable.SPEED + 20f + Turntable.SPEED, table.yaw(6000))
    }

    @Test
    fun `it never tips over`() {
        table.press(0.0, 0.0, 0)
        table.drag(0.0, 1000.0)
        assertEquals(Turntable.MAX_PITCH, table.pitch)
        table.drag(0.0, -1000.0)
        assertEquals(-Turntable.MAX_PITCH, table.pitch)
    }

    @Test
    fun `escape during a drag puts it back`() {
        table.yaw(0)
        table.press(0.0, 0.0, 1000)
        table.drag(50.0, 5.0)
        assertTrue(table.cancel(2000))
        assertEquals(Turntable.SPEED, table.yaw(2000))
        assertEquals(0f, table.pitch)
        assertFalse(table.cancel(2000))
    }

    @Test
    fun `it turns on the game's clock, which is epoch milliseconds`() {
        val now = 1_794_697_200_000L
        assertEquals(0f, table.yaw(now))
        assertEquals(Turntable.SPEED, table.yaw(now + 1000))
        assertEquals(0f, table.yaw(now + 12_000))
        table.press(0.0, 0.0, now + 1000)
        table.drag(10.0, 0.0)
        assertEquals(Turntable.SPEED + 20f, table.yaw(now + 1000))
    }

    @Test
    fun `an arrow key turns it by a step`() {
        table.turn(Turntable.KEY_YAW, Turntable.KEY_PITCH, 0)
        assertEquals(Turntable.KEY_YAW, table.yaw(0))
        assertEquals(Turntable.KEY_PITCH, table.pitch)
    }
}
