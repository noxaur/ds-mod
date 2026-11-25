package io.github.noxaur.dragen.core.ui

class TypeAhead {
    private var typed = ""
    private var last = 0L

    fun type(char: Char, now: Long, labels: List<String>, lit: Int): Int {
        if (labels.isEmpty()) return lit
        if (typed.isNotEmpty() && now - last > WINDOW) typed = ""
        last = now
        typed += char
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
