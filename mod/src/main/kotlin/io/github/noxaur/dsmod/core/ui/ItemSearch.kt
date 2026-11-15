package io.github.noxaur.dsmod.core.ui

/** The rule for Menu Search: which items a query matches. */
object ItemSearch {
    /** The words of [query], lowercased. An item matches when it holds every one. */
    fun words(query: String) = query.lowercase().split(' ').filter { it.isNotEmpty() }

    /** Whether an item named [name] with [lore] holds every one of [words], anywhere in its text, ignoring case. */
    fun matches(words: List<String>, name: String, lore: List<String>): Boolean {
        val lowerName = name.lowercase()
        return words.all { word -> word in lowerName || lore.any { it.lowercase().contains(word) } }
    }
}
