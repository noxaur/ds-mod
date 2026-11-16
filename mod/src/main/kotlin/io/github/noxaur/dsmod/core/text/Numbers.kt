package io.github.noxaur.dsmod.core.text

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

/** [number] with a comma between each three digits, such as 1,234,567. */
fun grouped(number: Long): String = String.format(Locale.US, "%,d", number)

/**
 * A number the server sends as a double: grouped and whole when it is whole, else to [places] places. With [trim],
 * the zeros at the end of the places go, so 1.50 reads 1.5.
 */
fun amount(value: Double, places: Int = 1, trim: Boolean = false): String {
    if (value % 1.0 == 0.0) return grouped(value.toLong())
    val shown = String.format(Locale.US, "%,.${places}f", value)
    return if (trim) shown.withoutTrailingZeros() else shown
}

private fun String.withoutTrailingZeros() = trimEnd('0').trimEnd('.')

private val SUFFIXES = arrayOf("", "k", "M", "B", "T")

/**
 * [value] shortened with k, M, B or T and up to [places] decimals, without the zeros at the end, so 1,234,567 reads
 * 1.2M. Under 1,000 it keeps its digits. Each step rounds before it is read, so 999,960 reads 1M and not 1000k.
 */
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
