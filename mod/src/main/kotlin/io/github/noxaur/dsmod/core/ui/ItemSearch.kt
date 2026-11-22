package io.github.noxaur.dsmod.core.ui

object ItemSearch {
    fun words(query: String) = query.lowercase().split(' ').filter { it.isNotEmpty() }

    fun matches(words: List<String>, name: String, lore: List<String>): Boolean {
        val lowerName = name.lowercase()
        return words.all { word -> word in lowerName || lore.any { it.lowercase().contains(word) } }
    }
}
