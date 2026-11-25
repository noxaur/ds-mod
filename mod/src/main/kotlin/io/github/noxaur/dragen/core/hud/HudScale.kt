package io.github.noxaur.dragen.core.hud

import io.github.noxaur.dragen.core.text.amount
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private const val EPSILON = 1e-4f

object HudScale {
    fun snap(scale: Float, guiScale: Int): Float = pixels(scale, guiScale.toFloat()).toFloat() / guiScale

    fun step(scale: Float, by: Int, factor: Float, guiScale: Int, min: Float, max: Float): Float {
        val unit = factor * guiScale
        val current = pixels(scale, unit)
        val low = maxOf(1, ceil(min * unit - EPSILON).toInt())
        val high = maxOf(low, floor(max * unit + EPSILON).toInt())
        val next = (current + by).coerceIn(low, high)
        return if (next == current || (next - current) * by < 0) scale else next / unit
    }

    fun label(scale: Float) = amount(scale.toDouble(), 2, trim = true) + "x"

    private fun pixels(scale: Float, unit: Float) = maxOf(1, (scale * unit).roundToInt())
}
