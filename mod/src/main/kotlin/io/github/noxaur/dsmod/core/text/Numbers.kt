package io.github.noxaur.dsmod.core.text

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

fun grouped(number: Long): String = String.format(Locale.US, "%,d", number)

fun amount(value: Double, places: Int = 1, trim: Boolean = false): String {
    if (value % 1.0 == 0.0) return grouped(value.toLong())
    val shown = String.format(Locale.US, "%,.${places}f", value)
    return if (trim) shown.withoutTrailingZeros() else shown
}

fun fixed(value: Double, places: Int): String = String.format(Locale.US, "%.${places}f", value)

fun count(text: String): Int? = text.replace(",", "").toIntOrNull()

fun number(text: String): Double? = text.replace(",", "").toDoubleOrNull()

fun perHour(count: Double, millis: Long): Double = count * HOUR_MILLIS / millis

private const val HOUR_MILLIS = 3_600_000.0

private fun String.withoutTrailingZeros() = trimEnd('0').trimEnd('.')

private val SUFFIXES = arrayOf("", "k", "M", "B", "T")

// @keep Each step rounds before it is read, so 999,960 reads 1M and not 1000k
fun short(value: Double, places: Int): String {
    val scale = 10.0.pow(places)
    var tier = 0
    var shown = Math.round(value * scale) / scale
    while (tier < SUFFIXES.lastIndex && abs(shown) >= 1000) {
        tier++
        shown = Math.round(value / 1000.0.pow(tier) * scale) / scale
    }
    val digits = String.format(Locale.US, "%.${places}f", shown)
    return (if (places > 0) digits.withoutTrailingZeros() else digits) + SUFFIXES[tier]
}

fun secondsLeft(millis: Long): Long = ((millis + 999) / 1000).coerceAtLeast(0)

fun duration(millis: Long, nearest: Boolean = false): String {
    val minutes = (millis + if (nearest) 30_000 else 59_999) / 60_000
    val hours = minutes / 60
    return when {
        hours >= 24 -> "${hours / 24}d ${hours % 24}h"
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}
