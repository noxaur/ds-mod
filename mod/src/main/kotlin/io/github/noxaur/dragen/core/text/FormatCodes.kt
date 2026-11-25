package io.github.noxaur.dragen.core.text

data class StyledRun(
    val text: String,
    val color: String? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underlined: Boolean = false,
    val strikethrough: Boolean = false,
    val obfuscated: Boolean = false,
)

object FormatCodes {
    private val NAMED = listOf(
        "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
        "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white",
    ).withIndex().associate { it.value to "0123456789abcdef"[it.index] }

    fun write(runs: List<StyledRun>): String = buildString {
        var last = StyledRun("")
        for (run in runs) {
            if (run.text.isEmpty()) continue
            val flags = flags(run)
            /** @keep A code can only add, so a color or a format that ends takes a reset first. A new color ends the formats
             * itself */
            val recolored = run.color != null && run.color != last.color
            if ((last.color != null && run.color == null) || (!recolored && flags(last).any { it !in flags })) {
                append("&r")
                last = StyledRun("")
            }
            val color = run.color
            if (color != null && color != last.color) {
                append('&').append(NAMED[color]?.toString() ?: color.lowercase())
                // @keep A color code clears the formats before it, as it does in the game
                for (flag in flags) append('&').append(flag)
            } else {
                for (flag in flags) if (flag !in flags(last)) append('&').append(flag)
            }
            append(run.text)
            last = run
        }
    }

    private fun flags(run: StyledRun) = buildString {
        if (run.obfuscated) append('k')
        if (run.bold) append('l')
        if (run.strikethrough) append('m')
        if (run.underlined) append('n')
        if (run.italic) append('o')
    }
}
