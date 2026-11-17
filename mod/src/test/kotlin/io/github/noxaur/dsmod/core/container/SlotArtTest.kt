package io.github.noxaur.dsmod.core.container

import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.feature.inventory.ItemRarity
import io.github.noxaur.dsmod.core.text.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SlotArtTest {
    private val rgb = Role.LEGENDARY.rgb
    private val opaque = 255 shl 24

    private fun at(art: IntArray, x: Int, y: Int) = art[y * SlotArt.SIZE + x]
    private fun alpha(art: IntArray, x: Int, y: Int) = at(art, x, y) ushr 24
    private fun color(art: IntArray, x: Int, y: Int) = at(art, x, y) and 0xFFFFFF

    @Test
    fun `a style with nothing in it paints nothing`() {
        val art = SlotArt.back(SlotStyle(Border.NONE, Fill.NONE), rgb, dark = false)
        assertTrue(art.all { it == 0 })
    }

    @Test
    fun `the square and gilded borders leave the corner pixels out, and the rounded one two more`() {
        for (border in listOf(Border.SQUARE, Border.GILDED)) {
            val art = SlotArt.back(SlotStyle(border), rgb, dark = false)
            assertEquals(0, alpha(art, 0, 0), "$border")
            assertEquals(255, alpha(art, 1, 0), "$border")
        }
        val rounded = SlotArt.back(SlotStyle(Border.ROUNDED), rgb, dark = false)
        assertEquals(0, alpha(rounded, 1, 0))
        assertEquals(255, alpha(rounded, 1, 1))
        assertEquals(opaque or rgb, at(rounded, 2, 0))
    }

    @Test
    fun `the flat border is a full square, corners and all`() {
        val art = SlotArt.back(SlotStyle(Border.FLAT, Fill.NONE), rgb, dark = false)
        for ((x, y) in listOf(0 to 0, 17 to 0, 0 to 17, 17 to 17, 8 to 0, 0 to 8)) assertEquals(opaque or rgb, at(art, x, y), "($x, $y)")
        assertEquals(0, alpha(art, 1, 1))
    }

    @Test
    fun `the gilded outline takes the shade on a light slot and the color on a dark one`() {
        val light = SlotArt.back(SlotStyle(Border.GILDED), rgb, dark = false)
        val dark = SlotArt.back(SlotStyle(Border.GILDED), rgb, dark = true)
        assertEquals(SlotArt.shade(rgb), color(light, 5, 0))
        assertEquals(rgb, color(dark, 5, 0))
        assertEquals(SlotArt.light(rgb), color(light, 5, 1))
    }

    @Test
    fun `the glow is even along each side, clear behind the item, and stronger on a light slot`() {
        val light = SlotArt.back(SlotStyle(Border.NONE, Fill.GLOW), rgb, dark = false)
        val dark = SlotArt.back(SlotStyle(Border.NONE, Fill.GLOW), rgb, dark = true)
        assertEquals(alpha(light, 1, 1), alpha(light, 8, 1))
        assertEquals(0, alpha(light, 8, 8))
        assertTrue(alpha(light, 8, 1) > alpha(dark, 8, 1))
        // On dark the glow leans toward white, so a dim color still reads as light.
        assertNotEquals(rgb, color(dark, 8, 1))
    }

    @Test
    fun `the wash tints the whole slot, the halo is strongest at its edge and leaves the corners`() {
        val wash = SlotArt.back(SlotStyle(Border.NONE, Fill.WASH), rgb, dark = true)
        assertTrue(alpha(wash, 8, 8) > 0)
        assertTrue(alpha(wash, 1, 8) > alpha(wash, 8, 8))
        val halo = SlotArt.back(SlotStyle(Border.NONE, Fill.HALO), rgb, dark = true)
        assertTrue(alpha(halo, 2, 9) > alpha(halo, 9, 9))
        assertEquals(0, alpha(halo, 1, 1))
    }

    @Test
    fun `the pedestal is lit on top and shaded below, along the slot's bottom`() {
        val art = SlotArt.back(SlotStyle(Border.NONE, Fill.NONE, Floor.PEDESTAL), rgb, dark = false)
        assertEquals(SlotArt.light(rgb), color(art, 2, 14))
        assertEquals(SlotArt.shade(rgb), color(art, 2, 16))
        assertEquals(0, alpha(art, 2, 13))
    }

    @Test
    fun `the border is drawn over the floor`() {
        // The gilded bevel's bottom row lies on the pedestal's shaded one.
        val art = SlotArt.back(SlotStyle(Border.GILDED, Fill.WASH, Floor.PEDESTAL), rgb, dark = false)
        assertEquals(opaque or rgb, at(art, 5, 16))
    }

    @Test
    fun `the gem has a dark outline and empty corners`() {
        val gem = SlotArt.gem(rgb)
        fun pixel(x: Int, y: Int) = gem[y * SlotArt.GEM + x]
        assertEquals(opaque or rgb, pixel(3, 3))
        assertEquals(0, pixel(0, 0))
        val outline = pixel(3, 0)
        assertEquals(0, outline and 0xFFFFFF)
        assertTrue(outline ushr 24 in 1..254)
    }

    @Test
    fun `a stepper wraps at both ends, and the presets differ`() {
        assertEquals(Border.GILDED, Border.NONE.step(-1, Border.entries))
        assertEquals(Border.NONE, Border.GILDED.step(1, Border.entries))
        val made = RarityPreset.entries.mapNotNull { it.style }
        assertEquals(made.size, made.toSet().size)
    }

    @Test
    fun `containers show the preset, and the player's own style only under Custom`() {
        val settings = SettingsTree().feature<ItemRarity>()
        settings.inventoryStyle.value = SlotStyle(Border.SQUARE, Fill.WASH)
        assertEquals(RarityPreset.GILDED.style, settings.containerStyle())
        settings.preset.value = RarityPreset.CUSTOM
        assertEquals(SlotStyle(Border.SQUARE, Fill.WASH), settings.containerStyle())
    }
}
