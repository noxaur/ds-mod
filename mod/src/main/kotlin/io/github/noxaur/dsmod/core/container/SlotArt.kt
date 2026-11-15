package io.github.noxaur.dsmod.core.container

import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Paints a [SlotStyle] from one color, as ARGB pixels that the edge uploads once and blits per slot. Every tone is
 * the color, a light tone mixed toward white or a shade mixed toward black.
 *
 * A dark slot and Minecraft's gray one need different art. On dark, a glow leans toward white so it reads as light,
 * where a dim blue or purple would sink into the black. On gray, a glow keeps the pure color at more strength,
 * because the gray washes a faint tint out, but never full strength: a glow that hides the gray turns a full chest
 * into a wall of solid color.
 */
object SlotArt {
    /** The art covers the slot's whole 18x18, one pixel around the item. */
    const val SIZE = 18

    /** The gem with its outline, drawn from the item's top-left pixel. */
    const val GEM = 7

    fun back(style: SlotStyle, rgb: Int, dark: Boolean): IntArray {
        val art = Canvas(SIZE)
        val glow = if (dark) light(rgb, 0.2) else rgb
        // Within the item's 16x16, which starts at (1, 1).
        fun inner(i: Int, j: Int, color: Int, alpha: Double) = art.put(i + 1, j + 1, color, if (dark) alpha else min(LIGHT_MAX, alpha * LIGHT_BOOST))

        when (style.fill) {
            Fill.NONE -> Unit
            Fill.GLOW -> each { i, j -> inner(i, j, glow, GLOW_BANDS.getOrElse(edge(i, j)) { 0.0 }) }
            Fill.WASH -> each { i, j ->
                val k = 1 - min(edge(i, j), WASH_REACH) / WASH_REACH.toDouble()
                inner(i, j, glow, ((WASH_FLOOR + WASH_RISE * k * k) * 8).roundToInt() / 8.0)
            }
            Fill.HALO -> each { i, j ->
                val d = hypot(i - CENTER, j - CENTER)
                inner(i, j, glow, HALO_RINGS.firstOrNull { d < it.first }?.second ?: 0.0)
            }
        }

        when (style.floor) {
            Floor.NONE -> Unit
            Floor.PEDESTAL -> {
                art.row(1, 14, 16, light(rgb))
                art.row(1, 15, 16, rgb)
                art.row(1, 16, 16, shade(rgb))
                for (i in intArrayOf(4, 10, 15)) art.put(i, 15, shade(rgb))
                for (i in intArrayOf(7, 13)) art.put(i, 14, rgb)
            }
            Floor.SPARKS -> {
                art.row(1, 15, 16, light(rgb))
                art.row(1, 16, 16, shade(rgb))
                for (i in intArrayOf(5, 12)) art.put(i, 16, rgb)
                for ((i, j) in SPARKS) art.put(i + 1, j + 1, light(rgb))
            }
            Floor.RISE -> {
                each { i, j -> if ((j - RISE_START) / RISE_SPAN * 16 > BAYER[j and 3][i and 3] + 0.5) inner(i, j, glow, RISE_ALPHA) }
                art.row(1, 16, 16, light(rgb))
            }
        }

        when (style.border) {
            Border.NONE -> Unit
            Border.FLAT -> {
                art.row(0, 0, SIZE, rgb)
                art.row(0, SIZE - 1, SIZE, rgb)
                art.column(0, 1, SIZE - 2, rgb)
                art.column(SIZE - 1, 1, SIZE - 2, rgb)
            }
            Border.SQUARE -> art.ring(rgb)
            Border.ROUNDED -> {
                art.row(2, 0, 14, rgb)
                art.row(2, 17, 14, rgb)
                art.column(0, 2, 14, rgb)
                art.column(17, 2, 14, rgb)
                for ((i, j) in ROUNDED_CORNERS) art.put(i, j, rgb)
            }
            Border.GILDED -> {
                // The kit's frame in the rarity's color. A shade outline vanishes on a dark slot, so there it takes the color.
                art.ring(if (dark) rgb else shade(rgb))
                art.row(1, 1, 16, light(rgb))
                art.column(1, 2, 15, light(rgb))
                val low = if (dark) shade(rgb) else rgb
                art.row(2, 16, 15, low)
                art.column(16, 2, 14, low)
            }
        }
        return art.pixels
    }

    /** A cut gem lit from the top-left, with a dark outline that keeps it apart from any item under it. */
    fun gem(rgb: Int): IntArray {
        val art = Canvas(GEM)
        val tone = mapOf('L' to light(rgb), 'C' to rgb, 'S' to shade(rgb))
        val on = { i: Int, j: Int -> GEM_ROWS.getOrNull(j)?.getOrNull(i)?.let { it != '.' } == true }
        for (j in -1..GEM_ROWS.size) {
            for (i in -1..GEM_ROWS[0].length) {
                if (!on(i, j) && (on(i - 1, j) || on(i + 1, j) || on(i, j - 1) || on(i, j + 1))) art.put(i + 1, j + 1, 0, OUTLINE_ALPHA)
            }
        }
        GEM_ROWS.forEachIndexed { j, row -> row.forEachIndexed { i, p -> if (p != '.') art.put(i + 1, j + 1, tone.getValue(p)) } }
        return art.pixels
    }

    fun light(rgb: Int, amount: Double = 0.5) = mix(rgb, 255, amount)

    fun shade(rgb: Int) = mix(rgb, 0, SHADE)

    private fun mix(rgb: Int, to: Int, amount: Double): Int {
        fun channel(shift: Int) = ((rgb shr shift and 255) + (to - (rgb shr shift and 255)) * amount).roundToInt()
        return channel(16) shl 16 or (channel(8) shl 8) or channel(0)
    }

    private inline fun each(paint: (Int, Int) -> Unit) {
        for (j in 0 until 16) for (i in 0 until 16) paint(i, j)
    }

    /** How many pixels the item-area pixel lies in from the nearest edge, so no corner glows more than a side. */
    private fun edge(i: Int, j: Int) = minOf(i, j, 15 - i, 15 - j)

    /** Straight-alpha pixels, painted back to front. */
    private class Canvas(val size: Int) {
        val pixels = IntArray(size * size)

        fun put(x: Int, y: Int, rgb: Int, alpha: Double = 1.0) {
            if (x !in 0 until size || y !in 0 until size || alpha <= 0) return
            val under = pixels[y * size + x]
            val underAlpha = (under ushr 24) / 255.0
            val outAlpha = alpha + underAlpha * (1 - alpha)
            fun channel(shift: Int) =
                (((rgb shr shift and 255) * alpha + (under shr shift and 255) * underAlpha * (1 - alpha)) / outAlpha).roundToInt()
            pixels[y * size + x] = (outAlpha * 255).roundToInt() shl 24 or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }

        fun row(x: Int, y: Int, length: Int, rgb: Int) {
            for (i in 0 until length) put(x + i, y, rgb)
        }

        fun column(x: Int, y: Int, length: Int, rgb: Int) {
            for (j in 0 until length) put(x, y + j, rgb)
        }

        /** A 1px ring around the whole art, with its four corner pixels left out. */
        fun ring(rgb: Int) {
            row(1, 0, size - 2, rgb)
            row(1, size - 1, size - 2, rgb)
            column(0, 1, size - 2, rgb)
            column(size - 1, 1, size - 2, rgb)
        }
    }

    private const val SHADE = 0.55
    private const val LIGHT_BOOST = 1.3
    private const val LIGHT_MAX = 0.8
    private const val CENTER = 7.5
    private val GLOW_BANDS = listOf(0.75, 0.55, 0.36, 0.2, 0.08)
    private const val WASH_REACH = 6
    private const val WASH_FLOOR = 0.26
    private const val WASH_RISE = 0.5
    private val HALO_RINGS = listOf(3.0 to 0.28, 5.0 to 0.42, 6.6 to 0.58, 8.0 to 0.78)
    private const val RISE_START = 5.0
    private const val RISE_SPAN = 10.0
    private const val RISE_ALPHA = 0.75
    private const val OUTLINE_ALPHA = 0.75
    private val BAYER = arrayOf(intArrayOf(0, 8, 2, 10), intArrayOf(12, 4, 14, 6), intArrayOf(3, 11, 1, 9), intArrayOf(15, 7, 13, 5))
    // Single motes, and a small cross around (12, 5).
    private val SPARKS = listOf(3 to 9, 7 to 2, 13 to 10, 2 to 4, 9 to 7, 12 to 5, 12 to 4, 12 to 6, 11 to 5, 13 to 5)
    private val ROUNDED_CORNERS = listOf(1 to 1, 16 to 1, 1 to 16, 16 to 16)
    private val GEM_ROWS = listOf("..L..", ".LCS.", "LCCCS", ".CCS.", "..S..")
}
