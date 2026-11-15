package io.github.noxaur.dsmod.core.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThirdPersonTest {
    @Test
    fun `F5 steps past the skipped view and never past first person`() {
        assertEquals(listOf(View.FIRST, View.BACK, View.FRONT), View.entries.map(Views.ALL::land))
        assertEquals(listOf(View.FIRST, View.BACK, View.FIRST), View.entries.map(Views.SKIP_FRONT::land))
        assertEquals(listOf(View.FIRST, View.FRONT, View.FRONT), View.entries.map(Views.SKIP_BACK::land))
        assertEquals(View.FRONT, Views.SKIP_BACK.third)
        assertEquals(View.BACK, Views.SKIP_FRONT.third)
    }

    @Test
    fun `the default distance leaves the game's own, and the setting scales a server's`() {
        assertEquals(4f, ThirdPerson.distance(4f, 4f))
        assertEquals(10f, ThirdPerson.distance(4f, 10f))
        assertEquals(20f, ThirdPerson.distance(8f, 10f))
    }

    @Test
    fun `the tilt stays within straight up and down, and turns over in the front view`() {
        assertEquals(30f, ThirdPerson.pitch(10f, 20f, front = false))
        assertEquals(-10f, ThirdPerson.pitch(10f, 20f, front = true))
        assertEquals(90f, ThirdPerson.pitch(80f, 45f, front = false))
    }

    @Test
    fun `no smoothing is at once`() {
        assertEquals(4f, Slide().at(4f, 0f, 1))
    }

    @Test
    fun `the camera slides out, arrives, and comes in at once for a wall`() {
        val slide = Slide()
        val second = 1_000_000_000L
        assertEquals(0f, slide.at(4f, 50f, second))
        val early = slide.at(4f, 50f, second + second / 60)
        assertTrue(early > 0f && early < 2f, "One frame in it is at $early")
        var now = second
        var at = 0f
        repeat(120) {
            now += second / 60
            at = slide.at(4f, 50f, now)
        }
        assertTrue(at > 3.99f, "After two seconds it is at $at")
        assertEquals(1f, slide.at(1f, 50f, now + second / 60))
        slide.reset()
        assertEquals(0f, slide.at(4f, 50f, now + second))
    }

    @Test
    fun `free look turns as far around as the mouse goes, and stops straight up and down`() {
        val orbit = Orbit(350f, 0f)
        orbit.turn(100.0, 1000.0)
        assertEquals(365f, orbit.yaw)
        assertEquals(90f, orbit.pitch)
    }
}
