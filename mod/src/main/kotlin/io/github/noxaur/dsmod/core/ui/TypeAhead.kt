package io.github.noxaur.dsmod.core.ui

/**
 * Typing to jump in an open menu, as desktop menus and list boxes do. A letter lights the next item whose label
 * starts with it, and the same letter again goes on to the next one, round to the first. Letters typed within
 * [WINDOW] ms of each other match the start of a label together, so "fl" finds Flat past Fill.
 */
class TypeAhead {
    private var typed = ""
    private var last = 0L

    /** The item to light after [char] is typed at [now], with [lit] lit. It stays [lit] when no label starts with what was typed. */
    fun type(char: Char, now: Long, labels: List<String>, lit: Int): Int {
        if (labels.isEmpty()) return lit
        if (typed.isNotEmpty() && now - last > WINDOW) typed = ""
        last = now
        typed += char
        // One letter, or the same one again, steps past the lit item. More letters keep it while it still matches.
        val again = typed.all { it.equals(typed[0], ignoreCase = true) }
        val prefix = if (again) typed.substring(0, 1) else typed
        val from = if (again) lit + 1 else lit.coerceAtLeast(0)
        for (k in labels.indices) {
            val i = (from + k).mod(labels.size)
            if (labels[i].startsWith(prefix, ignoreCase = true)) return i
        }
        return lit
    }

    companion object {
        const val WINDOW = 1000L
    }
}
