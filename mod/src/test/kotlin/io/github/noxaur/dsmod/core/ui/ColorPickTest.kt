package io.github.noxaur.dsmod.core.ui

import io.github.noxaur.dsmod.core.config.Bind
import io.github.noxaur.dsmod.core.ui.ColorPick.Part
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColorPickTest {
    private val orange = 0xFFFF8800.toInt()

    @Test
    fun `a color reads back as the hue, saturation and brightness it was built from`() {
        val pick = ColorPick(orange)
        assertEquals(32f, pick.hue, 0.1f)
        assertEquals(1f, pick.saturation)
        assertEquals(1f, pick.brightness)
        assertEquals(orange, pick.argb)
        for (argb in listOf(0xFF123456, 0x80ABCDEF, 0xFF000000, 0xFFFFFFFF, 0x00FF0000).map { it.toInt() }) {
            val again = ColorPick(argb)
            again.field(again.saturation.toDouble(), again.brightness.toDouble())
            assertEquals(argb, again.argb, Colors.show(argb))
        }
    }

    @Test
    fun `the hue stays where it was while the color is gray, and the saturation while it is black`() {
        val pick = ColorPick(orange)
        pick.field(0.0, 0.5)
        pick.take(pick.argb)
        assertEquals(32f, pick.hue, 0.1f)
        pick.field(1.0, 0.0)
        pick.take(0xFF000000.toInt())
        assertEquals(1f, pick.saturation)
        pick.field(1.0, 1.0)
        assertEquals(orange, pick.argb)
    }

    @Test
    fun `the field, the hue and the opacity clamp what lies past their ends`() {
        val pick = ColorPick(orange)
        pick.field(2.0, -1.0)
        assertEquals(1f, pick.saturation)
        assertEquals(0f, pick.brightness)
        pick.hueAt(1.5)
        assertEquals(360f, pick.hue)
        pick.opacityAt(-1.0)
        assertEquals(0, Colors.alpha(pick.argb))
    }

    @Test
    fun `the arrow keys nudge a percent or a degree, and ten with Shift`() {
        val pick = ColorPick(ColorPick.hsv(100f, 0.5f, 0.5f) or (0xFF shl 24))
        assertTrue(pick.nudge(Part.FIELD, 1, -1, big = false))
        assertEquals(0.51f, pick.saturation, 0.001f)
        assertEquals(0.51f, pick.brightness, 0.001f)
        assertTrue(pick.nudge(Part.HUE, -1, 0, big = true))
        assertEquals(90f, pick.hue, 0.5f)
        assertTrue(pick.nudge(Part.OPACITY, -1, 0, big = true))
        assertEquals(90, Colors.opacity(pick.argb))
        assertFalse(pick.nudge(Part.HUE, 0, 1, big = false))
        assertFalse(pick.nudge(Part.SWATCHES, 1, 0, big = false))
        val dark = ColorPick(0xFF010100.toInt())
        assertTrue(dark.nudge(Part.FIELD, 1, 0, big = false))
        assertEquals(0xFF010100.toInt(), dark.argb, "Right at full saturation leaves the brightness alone")
    }

    @Test
    fun `restoring what was held brings back the hue and saturation that black hides`() {
        val green = ColorPick(0xFF00FF00.toInt())
        val held = green.hold()
        green.field(1.0, 0.0)
        green.hueAt(240 / 360.0)
        green.restore(held)
        green.field(1.0, 1.0)
        assertEquals(0xFF00FF00.toInt(), green.argb)
    }

    @Test
    fun `hex takes one, two, three, six or eight digits, with or without the hash`() {
        assertEquals(0xFFFFFFFF.toInt(), Colors.parseHex("f"))
        assertEquals(0xFFABABAB.toInt(), Colors.parseHex("#AB"))
        assertEquals(0xFFFF8800.toInt(), Colors.parseHex("f80"))
        assertEquals(0x80FF8800.toInt(), Colors.parseHex(" FF8800 ", alpha = 0x80))
        assertEquals(0x40FF8800, Colors.parseHex("#FF880040"))
        for (bad in listOf("", "#", "FF88", "GG0000", "#FF88001")) assertNull(Colors.parseHex(bad), bad)
    }

    @Test
    fun `opacity is a whole percent, clamped, with or without its sign`() {
        assertEquals(50, Colors.parseOpacity("50%"))
        assertEquals(100, Colors.parseOpacity("140"))
        assertEquals(0, Colors.parseOpacity("-3 %"))
        assertNull(Colors.parseOpacity("half"))
        assertEquals(0x80, Colors.alpha(Colors.withOpacity(orange, 50)))
        assertEquals(50, Colors.opacity(Colors.withOpacity(orange, 50)))
    }

    @Test
    fun `a color shows as hex, with its opacity only when it is see-through`() {
        assertEquals("#FF8800", Colors.show(orange))
        assertEquals("#FF8800 50%", Colors.show(Colors.withOpacity(orange, 50)))
        assertEquals("#FF8800, 100% opacity", Colors.spoken(orange))
    }

    @Test
    fun `recent colors put the newest first, once each, and keep the last few`() {
        var saved = emptyList<Int>()
        val recent = RecentColors(Bind("recent", { saved }, { saved = it }), max = 3)
        for (argb in listOf(1, 2, 3, 2, 4)) recent.add(argb)
        assertEquals(listOf(4, 2, 3), saved)
    }

    @Test
    fun `the narrator says the part the keys are on and its value`() {
        val pick = ColorPick(orange)
        assertEquals("Saturation 100%, brightness 100%", pick.narration(Part.FIELD))
        assertEquals("Hue 32 degrees", pick.narration(Part.HUE))
        assertEquals("Opacity 100%", pick.narration(Part.OPACITY))
        assertEquals("Hex, #FF8800", pick.narration(Part.HEX))
        assertEquals("#FF8800, swatch, 2 of 2, current", pick.narration(Part.SWATCHES, listOf(0xFF000000.toInt(), orange), 1))
    }
}
