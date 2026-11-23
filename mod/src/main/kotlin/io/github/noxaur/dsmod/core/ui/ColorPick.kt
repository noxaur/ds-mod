package io.github.noxaur.dsmod.core.ui

import io.github.noxaur.dsmod.core.config.Bind
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.number
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object Colors {
    const val OPAQUE = 0xFF

    val PRESETS = Role.CHAT_COLORS.map { it or (OPAQUE shl 24) }

    fun alpha(argb: Int) = argb ushr 24

    fun rgb(argb: Int) = argb shl 8 ushr 8

    fun opacity(argb: Int) = (alpha(argb) * 100 / 255.0).roundToInt()

    fun withOpacity(argb: Int, percent: Int) = ((percent.coerceIn(0, 100) * 255 / 100.0).roundToInt() shl 24) or rgb(argb)

    fun hex(argb: Int): String = String.format(Locale.ROOT, "#%06X", rgb(argb))

    fun show(argb: Int) = if (alpha(argb) == OPAQUE) hex(argb) else "${hex(argb)} ${opacity(argb)}%"

    fun spoken(argb: Int) = Narration.join(hex(argb), "${opacity(argb)}% opacity")

    fun parseHex(text: String, alpha: Int = OPAQUE): Int? {
        val digits = text.trim().removePrefix("#")
        if (digits.isEmpty() || digits.any { Character.digit(it, 16) < 0 }) return null
        val full = when (digits.length) {
            1 -> digits.repeat(6)
            2 -> digits.repeat(3)
            3 -> digits.map { "$it$it" }.joinToString("")
            6, 8 -> digits
            else -> return null
        }
        val rgb = full.substring(0, 6).toInt(16)
        val a = if (full.length == 8) full.substring(6).toInt(16) else alpha
        return (a shl 24) or rgb
    }

    fun parseOpacity(text: String): Int? = number(text.trim().removeSuffix("%").trim())?.takeIf { !it.isNaN() }?.roundToInt()?.coerceIn(0, 100)
}

class RecentColors(private val bind: Bind<List<Int>>, private val max: Int = 8) {
    val colors: List<Int> get() = bind.get()

    fun add(argb: Int) = bind.set((listOf(argb) + colors.filter { it != argb }).take(max))
}

class ColorPick(argb: Int) {
    enum class Part { FIELD, HUE, OPACITY, HEX, PERCENT, SWATCHES }

    var hue = 0f
        private set
    var saturation = 0f
        private set
    var brightness = 0f
        private set
    var alpha = Colors.OPAQUE
        private set

    var argb = argb.inv()
        private set

    init {
        take(argb)
    }

    fun take(next: Int) {
        if (next == argb) return
        alpha = Colors.alpha(next)
        val r = (next shr 16 and 0xFF) / 255f
        val g = (next shr 8 and 0xFF) / 255f
        val b = (next and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val range = max - minOf(r, g, b)
        brightness = max
        if (max > 0f) saturation = range / max
        if (range > 0f) {
            val h = when (max) {
                r -> (g - b) / range
                g -> (b - r) / range + 2f
                else -> (r - g) / range + 4f
            }
            hue = (h * 60f + 360f) % 360f
        }
        argb = next
    }

    fun field(saturation: Double, brightness: Double) {
        this.saturation = saturation.coerceIn(0.0, 1.0).toFloat()
        this.brightness = brightness.coerceIn(0.0, 1.0).toFloat()
        build()
    }

    fun hueAt(fraction: Double) {
        hue = (fraction.coerceIn(0.0, 1.0) * 360.0).toFloat()
        build()
    }

    fun opacityAt(fraction: Double) {
        alpha = (fraction.coerceIn(0.0, 1.0) * 255).roundToInt()
        build()
    }

    val hueFraction get() = hue / 360f

    val opacityFraction get() = alpha / 255f

    val pureHue get() = hsv(hue, 1f, 1f) or (Colors.OPAQUE shl 24)

    fun nudge(part: Part, dx: Int, dy: Int, big: Boolean): Boolean {
        val by = if (big) 10 else 1
        when (part) {
            Part.FIELD -> field(step(saturation, dx * by), step(brightness, -dy * by))
            Part.HUE -> if (dx != 0) hueAt((hue + dx * by).coerceIn(0f, 360f) / 360.0) else return false
            Part.OPACITY -> if (dx != 0) opacityAt(Colors.alpha(Colors.withOpacity(argb, Colors.opacity(argb) + dx * by)) / 255.0) else return false
            else -> return false
        }
        return true
    }

    private fun step(value: Float, percent: Int) = ((value * 100).roundToInt() + percent) / 100.0

    private fun build() {
        argb = (alpha shl 24) or hsv(hue, saturation, brightness)
    }

    fun narration(part: Part, swatches: List<Int> = emptyList(), lit: Int = -1): String = when (part) {
        Part.FIELD -> "Saturation ${percent(saturation)}%, brightness ${percent(brightness)}%"
        Part.HUE -> "Hue ${hue.roundToInt()} degrees"
        Part.OPACITY -> "Opacity ${Colors.opacity(argb)}%"
        Part.HEX -> Narration.join("Hex", Colors.hex(argb))
        Part.PERCENT -> Narration.join("Opacity", "${Colors.opacity(argb)}%")
        Part.SWATCHES -> swatches.getOrNull(lit)?.let { Narration.join(Colors.show(it), "swatch", Narration.of(lit, swatches.size), "current".takeIf { _ -> it == argb }) }.orEmpty()
    }

    private fun percent(value: Float) = (value * 100).roundToInt()

    companion object {
        fun hsv(hue: Float, saturation: Float, brightness: Float): Int {
            val h = (hue % 360f) / 60f
            val c = brightness * saturation
            val x = c * (1 - abs(h % 2 - 1))
            val m = brightness - c
            val (r, g, b) = when (h.toInt()) {
                0 -> Triple(c, x, 0f)
                1 -> Triple(x, c, 0f)
                2 -> Triple(0f, c, x)
                3 -> Triple(0f, x, c)
                4 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            fun channel(v: Float) = ((v + m) * 255).roundToInt().coerceIn(0, 255)
            return (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
        }
    }
}
