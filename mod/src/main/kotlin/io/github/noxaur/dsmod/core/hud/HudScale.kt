package io.github.noxaur.dsmod.core.hud

import io.github.noxaur.dsmod.core.text.amount
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private const val EPSILON = 1e-4f

/** The HUD's scale snaps to whole screen pixels, so a 1px edge stays one pixel wide at every GUI scale. */
object HudScale {
    /** [scale] rounded so that one HUD pixel covers a whole number of screen pixels at [guiScale]. */
    fun snap(scale: Float, guiScale: Int): Float = pixels(scale, guiScale.toFloat()).toFloat() / guiScale

    /**
     * [scale] moved by [by] screen pixels per HUD pixel, so every step shows. The drawn size is [scale]
     * times [factor] at [guiScale], and the result stays within [min] and [max] when a step allows.
     */
    fun step(scale: Float, by: Int, factor: Float, guiScale: Int, min: Float, max: Float): Float {
        val unit = factor * guiScale
        val current = pixels(scale, unit)
        val low = maxOf(1, ceil(min * unit - EPSILON).toInt())
        val high = maxOf(low, floor(max * unit + EPSILON).toInt())
        val next = (current + by).coerceIn(low, high)
        // A scale already past a bound, as a docked block's can be, never steps the wrong way to reach it.
        return if (next == current || (next - current) * by < 0) scale else next / unit
    }

    /** [scale] as the HUD editors show it, such as `1.25x`. */
    fun label(scale: Float) = amount(scale.toDouble(), 2, trim = true) + "x"

    private fun pixels(scale: Float, unit: Float) = maxOf(1, (scale * unit).roundToInt())
}
