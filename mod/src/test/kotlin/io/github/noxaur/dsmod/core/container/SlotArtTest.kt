package io.github.noxaur.dsmod.core.container

import io.github.noxaur.dsmod.core.text.Role
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SlotArtTest {
    private val rgb = Role.LEGENDARY.rgb

    private fun at(art: IntArray, x: Int, y: Int) = art[y * SlotArt.SIZE + x]
    private fun alpha(art: IntArray, x: Int, y: Int) = at(art, x, y) ushr 24
    private fun color(art: IntArray, x: Int, y: Int) = at(art, x, y) and 0xFFFFFF
    private fun back(mark: MarkStyle, dark: Boolean = true, sparkles: Boolean = false, variant: Int = 0) =
        SlotArt.back(SlotStyle(mark, sparkles = sparkles), rgb, dark, variant)

    @Test
    fun `every style paints one color, leaning toward white on a dark slot`() {
        for (mark in MarkStyle.entries) {
            val light = back(mark, dark = false)
            val dark = back(mark, dark = true)
            assertEquals(setOf(rgb), light.filter { it ushr 24 > 0 }.map { it and 0xFFFFFF }.toSet(), "$mark on light")
            assertEquals(setOf(SlotArt.light(rgb, 0.2)), dark.filter { it ushr 24 > 0 }.map { it and 0xFFFFFF }.toSet(), "$mark on dark")
        }
    }

    @Test
    fun `the frame is strongest at the item area's edge and never bare in the middle, with nothing outside it`() {
        val art = back(MarkStyle.FRAME)
        assertEquals(0, alpha(art, 0, 8), "the slot's own bevel stays visible")
        assertTrue(alpha(art, 1, 8) > alpha(art, 2, 8))
        assertEquals(alpha(art, 1, 1), alpha(art, 8, 1), "a corner glows no more than a side")
        assertTrue(alpha(art, 8, 8) > 0)
    }

    @Test
    fun `the aura's corners are clear and its rim pixels partly see-through, which makes it read as round`() {
        val art = back(MarkStyle.AURA)
        assertEquals(0, alpha(art, 1, 1))
        val rim = alpha(art, 4, 2)
        assertTrue(rim in 1 until alpha(art, 8, 2), "an edge pixel the circle half covers is fainter than a covered one")
        assertTrue(alpha(art, 8, 2) > alpha(art, 8, 8), "strongest at the rim")
        assertTrue(alpha(art, 8, 8) > 0)
    }

    @Test
    fun `the underglow pools under the item and fades up`() {
        val art = back(MarkStyle.UNDERGLOW)
        assertTrue(alpha(art, 8, 17) > alpha(art, 8, 12))
        assertTrue(alpha(art, 8, 17) > alpha(art, 1, 17))
        assertEquals(0, alpha(art, 8, 2))
    }

    @Test
    fun `a gray slot gets more of the color, but never all of it`() {
        for (mark in MarkStyle.entries) {
            val light = back(mark, dark = false)
            val dark = back(mark, dark = true)
            assertTrue(light.indices.all { (light[it] ushr 24) >= minOf(dark[it] ushr 24, 204) }, "$mark")
            assertTrue(light.all { (it ushr 24) <= 204 }, "$mark hides the gray")
        }
    }

    @Test
    fun `sparkles glint inside the colored area, away from the stack count, and each placement differs`() {
        for (mark in MarkStyle.entries) {
            val plain = back(mark)
            val placements = (0 until SlotArt.VARIANTS).map { back(mark, sparkles = true, variant = it) }
            for (art in placements) {
                assertTrue(art.contentEquals(plain).not(), "$mark has no sparkle")
                for (i in art.indices) {
                    if (art[i] == plain[i]) continue
                    assertTrue(plain[i] ushr 24 > 12, "$mark sparkles outside its color at ${i % SlotArt.SIZE}, ${i / SlotArt.SIZE}")
                }
                assertTrue(art.indices.any { art[it] and 0xFFFFFF == 0xFFFFFF }, "$mark has no white center")
            }
            assertEquals(SlotArt.VARIANTS, placements.map { it.toList() }.toSet().size, "$mark repeats a placement")
            for (art in placements) for (y in 10 until SlotArt.SIZE) for (x in 10 until SlotArt.SIZE) assertNotEquals(0xFFFFFF, color(art, x, y), "$mark at $x, $y")
        }
    }

    @Test
    fun `the same placement paints the same pixels every time, and none without sparkles`() {
        assertArrayEquals(back(MarkStyle.AURA, sparkles = true, variant = 2), back(MarkStyle.AURA, sparkles = true, variant = 2))
        assertArrayEquals(back(MarkStyle.AURA, variant = 0), back(MarkStyle.AURA, variant = 3))
    }

    @Test
    fun `opacity is drawn, not painted`() {
        assertArrayEquals(SlotArt.back(SlotStyle(MarkStyle.FRAME), rgb, true), SlotArt.back(SlotStyle(MarkStyle.FRAME, opacity = 40), rgb, true))
        assertFalse(SlotStyle().sparkles)
    }
}
