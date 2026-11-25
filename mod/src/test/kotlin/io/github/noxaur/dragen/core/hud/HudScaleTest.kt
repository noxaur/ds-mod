package io.github.noxaur.dragen.core.hud

import kotlin.test.Test
import kotlin.test.assertEquals

class HudScaleTest {
    @Test
    fun `a scale snaps to whole screen pixels`() {
        assertEquals(1.5f, HudScale.snap(1.5f, 2))
        assertEquals(1.5f, HudScale.snap(1.6f, 2))
        assertEquals(2f, HudScale.snap(1.5f, 1))
        assertEquals(4f / 3, HudScale.snap(1.3f, 3))
    }

    @Test
    fun `a HUD pixel never covers less than one screen pixel`() {
        assertEquals(1f, HudScale.snap(0.1f, 1))
        assertEquals(0.25f, HudScale.snap(0.1f, 4))
    }

    @Test
    fun `a step changes the drawn size by one screen pixel`() {
        assertEquals(1.5f, HudScale.step(1f, 1, 1f, 2, 0.5f, 3f))
        assertEquals(0.5f, HudScale.step(1f, -1, 1f, 2, 0.5f, 3f))
        val next = HudScale.step(1f, 1, 1.5f, 2, 0.5f, 4f)
        assertEquals(4, Math.round(HudScale.snap(next * 1.5f, 2) * 2))
    }

    @Test
    fun `a step stops at the bounds`() {
        assertEquals(3f, HudScale.step(3f, 1, 1f, 2, 0.5f, 3f))
        assertEquals(0.5f, HudScale.step(0.5f, -1, 1f, 2, 0.5f, 3f))
        assertEquals(0.5f, HudScale.step(0.5f, -1, 1f, 1, 0.5f, 3f))
    }

    @Test
    fun `a scale past a bound never steps the wrong way`() {
        assertEquals(1f / 3, HudScale.step(1f / 3, -1, 3f, 2, 0.5f, 4f))
        assertEquals(0.5f, HudScale.step(1f / 3, 1, 3f, 2, 0.5f, 4f))
    }
}
