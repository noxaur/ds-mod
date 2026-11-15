package io.github.noxaur.dsmod.core.hand

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeldItemTest {
    @Test
    fun `speed 1 leaves the game's own swing, even one Haste cut to nothing`() {
        assertEquals(6, HeldItem.swingTicks(6, 1f))
        assertEquals(0, HeldItem.swingTicks(0, 1f))
    }

    @Test
    fun `a faster swing is shorter and never under a tick, and a slower one longer`() {
        assertEquals(3, HeldItem.swingTicks(6, 2f))
        assertEquals(2, HeldItem.swingTicks(6, 3f))
        assertEquals(1, HeldItem.swingTicks(2, 3f))
        assertEquals(12, HeldItem.swingTicks(6, 0.5f))
        assertEquals(60, HeldItem.swingTicks(6, 0.1f))
    }

    @Test
    fun `the pose eases in over a few frames, holds, and eases back out to nothing`() {
        val ease = Ease()
        val second = 1_000_000_000L
        assertEquals(0f, ease.at(1f, second))
        val frame = second / 60
        val early = ease.at(1f, second + frame)
        assertTrue(early > 0.2f && early < 0.6f, "One frame in it is at $early")
        var now = second + frame
        repeat(12) {
            now += frame
            ease.at(1f, now)
        }
        assertTrue(ease.shown > 0.99f, "After a fifth of a second it is at ${ease.shown}")
        repeat(30) {
            now += frame
            ease.at(0f, now)
        }
        assertEquals(0f, ease.shown)
    }
}
