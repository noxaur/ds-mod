package io.github.noxaur.dsmod.core.text

import java.util.Locale

/** [number] with a comma between each three digits, such as 1,234,567. */
fun grouped(number: Long): String = String.format(Locale.US, "%,d", number)

/**
 * A number the server sends as a double: grouped and whole when it is whole, else to [places] places. With [trim],
 * the zeros at the end of the places go, so 1.50 reads 1.5.
 */
fun amount(value: Double, places: Int = 1, trim: Boolean = false): String {
    if (value % 1.0 == 0.0) return grouped(value.toLong())
    val shown = String.format(Locale.US, "%,.${places}f", value)
    return if (trim) shown.trimEnd('0').trimEnd('.') else shown
}

/** A wait of [millis], in whole minutes rounded up, as `45m`, `3h 5m` or `2d 4h`. */
fun duration(millis: Long): String {
    val minutes = (millis + 59_999) / 60_000
    val hours = minutes / 60
    return when {
        hours >= 24 -> "${hours / 24}d ${hours % 24}h"
        hours > 0 -> "${hours}h ${minutes % 60}m"
        else -> "${minutes}m"
    }
}
