package io.github.noxaur.dragen.core.container

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

object SlotArt {
    const val SIZE = 18

    const val VARIANTS = 4

    fun back(style: SlotStyle, rgb: Int, dark: Boolean, variant: Int = 0): IntArray {
        val art = Canvas(SIZE)
        val glow = if (dark) light(rgb, 0.2) else rgb
        fun boost(alpha: Double) = if (dark) alpha else min(LIGHT_MAX, alpha * LIGHT_BOOST)

        when (style.mark) {
            MarkStyle.FRAME -> for (j in 0 until 16) for (i in 0 until 16) {
                art.put(i + 1, j + 1, glow, boost(FRAME_BANDS.getOrElse(minOf(i, j, 15 - i, 15 - j)) { FRAME_INSIDE }))
            }
            MarkStyle.AURA -> for (y in 1..16) for (x in 1..16) {
                val cover = cover(x, y)
                if (cover <= 0) continue
                val rim = AURA_RADIUS - hypot(x + 0.5 - AURA_CENTER, y + 0.5 - AURA_CENTER)
                art.put(x, y, glow, boost(AURA_BANDS.firstOrNull { rim < it.first }?.second ?: AURA_INSIDE) * cover)
            }
            MarkStyle.UNDERGLOW -> for (y in 0 until SIZE) for (x in 0 until SIZE) {
                val d = hypot((x - UNDER_X) / UNDER_WIDTH, (y - UNDER_Y) / UNDER_HEIGHT)
                if (d < 1) art.put(x, y, glow, boost((UNDER_PEAK * (1 - d).pow(UNDER_FALL) * 8).roundToInt() / 8.0))
            }
        }
        if (style.sparkles) sparkle(art, rgb, variant)
        return art.pixels
    }

    private fun sparkle(art: Canvas, rgb: Int, variant: Int) {
        val colored = BooleanArray(SIZE * SIZE) { art.alpha(it) > COLORED }
        val spots = ArrayList<Int>()
        for (y in 0 until SIZE) for (x in 0 until SIZE) if (art.alpha(y * SIZE + x) >= SPOT && !(x >= COUNT_CORNER && y >= COUNT_CORNER)) spots += y * SIZE + x
        if (spots.isEmpty()) return
        val random = Mulberry32(variant * SEED + rgb)
        val picked = ArrayList<Int>()
        for (tries in 0 until TRIES) {
            if (picked.size == 3) break
            val spot = spots[(random.next() * spots.size).toInt()]
            if (picked.all { abs(it % SIZE - spot % SIZE) + abs(it / SIZE - spot / SIZE) >= APART }) picked += spot
        }
        fun put(x: Int, y: Int, color: Int, alpha: Double = 1.0) {
            if (x in 0 until SIZE && y in 0 until SIZE && colored[y * SIZE + x]) art.put(x, y, color, alpha)
        }
        for ((n, spot) in picked.withIndex()) {
            val x = spot % SIZE
            val y = spot / SIZE
            when (n) {
                0 -> {
                    put(x, y, light(rgb, 1.0))
                    for ((dx, dy) in ARMS) {
                        put(x + dx, y + dy, light(rgb, 0.6))
                        put(x + dx * 2, y + dy * 2, light(rgb, 0.3), TIP_ALPHA)
                    }
                }
                1 -> {
                    put(x, y, light(rgb, 1.0))
                    for ((dx, dy) in ARMS) put(x + dx, y + dy, light(rgb, 0.5), TIP_ALPHA)
                }
                else -> put(x, y, light(rgb, 0.7))
            }
        }
    }

    private fun cover(x: Int, y: Int): Double {
        var inside = 0
        for (v in 0 until 4) for (u in 0 until 4) if (hypot(x + (u + 0.5) / 4 - AURA_CENTER, y + (v + 0.5) / 4 - AURA_CENTER) <= AURA_RADIUS) inside++
        return inside / 16.0
    }

    fun light(rgb: Int, amount: Double = 0.5) = mix(rgb, 255, amount)

    private fun mix(rgb: Int, to: Int, amount: Double): Int {
        fun channel(shift: Int) = ((rgb shr shift and 255) + (to - (rgb shr shift and 255)) * amount).roundToInt()
        return channel(16) shl 16 or (channel(8) shl 8) or channel(0)
    }

    private class Mulberry32(private var state: Int) {
        private companion object {
            const val GOLDEN_GAMMA = 1_831_565_813
        }

        fun next(): Double {
            state += GOLDEN_GAMMA
            var t = (state xor (state ushr 15)) * (1 or state)
            t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
            return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0
        }
    }

    private class Canvas(val size: Int) {
        val pixels = IntArray(size * size)

        fun alpha(index: Int) = (pixels[index] ushr 24) / 255.0

        fun put(x: Int, y: Int, rgb: Int, alpha: Double = 1.0) {
            if (x !in 0 until size || y !in 0 until size || alpha <= 0) return
            val under = pixels[y * size + x]
            val underAlpha = (under ushr 24) / 255.0
            val outAlpha = alpha + underAlpha * (1 - alpha)
            fun channel(shift: Int) =
                (((rgb shr shift and 255) * alpha + (under shr shift and 255) * underAlpha * (1 - alpha)) / outAlpha).roundToInt()
            pixels[y * size + x] = (outAlpha * 255).roundToInt() shl 24 or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
    }

    private const val LIGHT_BOOST = 1.3
    private const val LIGHT_MAX = 0.8
    private val FRAME_BANDS = listOf(0.75, 0.5, 0.36, 0.3)
    private const val FRAME_INSIDE = 0.28
    private const val AURA_CENTER = 9.0
    private const val AURA_RADIUS = 8.0
    private val AURA_BANDS = listOf(1.2 to 0.75, 2.2 to 0.5)
    private const val AURA_INSIDE = 0.36
    private const val UNDER_X = 8.5
    private const val UNDER_Y = 17.5
    private const val UNDER_WIDTH = 11.0
    private const val UNDER_HEIGHT = 12.0
    private const val UNDER_PEAK = 0.95
    private const val UNDER_FALL = 0.9
    private const val COLORED = 0.05
    private const val SPOT = 0.3
    private const val COUNT_CORNER = 10
    private const val SEED = 7919
    private const val TRIES = 200
    private const val APART = 6
    private const val TIP_ALPHA = 0.8
    private val ARMS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
}
