package io.github.noxaur.dsmod.core.ui

import io.github.noxaur.dsmod.core.config.Order
import io.github.noxaur.dsmod.core.config.Setting
import io.github.noxaur.dsmod.core.config.SettingGroup
import io.github.noxaur.dsmod.core.config.SettingSection

data class SettingHit(val section: Int, val group: SettingGroup, val rows: List<Setting>, val open: Set<String>)

object SettingsSearch {
    fun words(query: String) = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }

    fun hits(sections: List<SettingSection>, query: String): List<SettingHit> {
        val words = words(query)
        if (words.isEmpty()) return emptyList()
        val hits = ArrayList<SettingHit>()
        for ((i, section) in sections.withIndex()) {
            for (group in section.groups) {
                val heading = listOfNotNull(group.name, group.switch?.name, group.switch?.description).joinToString(" ").lowercase()
                val open = HashSet<String>()
                val rows = group.rows.filter { row -> row.visible && found(row, heading, words, open) != Found.NO }
                if (rows.isNotEmpty()) hits += SettingHit(i, group, rows, open)
            }
        }
        return hits
    }

    private enum class Found { NO, ABOVE, SELF }

    private fun found(row: Setting, above: String, words: List<String>, open: MutableSet<String>): Found {
        var below = false
        for (child in row.children) if (child.visible && found(child, "$above ${row.name.lowercase()}", words, open) == Found.SELF) below = true
        if (below) open += row.id
        val text = "$above ${row.searchText}"
        if (row is Order<*> && words.all { it in text } && words.any { word -> row.labels.any { word in it.lowercase() } }) open += row.id
        return when {
            below || words.all { it in text } && !words.all { it in above } -> Found.SELF
            words.all { it in text } -> Found.ABOVE
            else -> Found.NO
        }
    }

    fun counts(hits: List<SettingHit>, size: Int) = IntArray(size).also { counts -> for (hit in hits) counts[hit.section] += hit.rows.size }

    fun pieces(text: String, words: List<String>): List<Piece> {
        if (words.isEmpty() || text.isEmpty()) return listOf(Piece(text, false))
        val hit = BooleanArray(text.length)
        for (word in words) {
            var at = text.indexOf(word, ignoreCase = true)
            while (at >= 0) {
                for (i in at until at + word.length) hit[i] = true
                at = text.indexOf(word, at + 1, ignoreCase = true)
            }
        }
        val pieces = ArrayList<Piece>()
        var from = 0
        for (i in 1..text.length) {
            if (i == text.length || hit[i] != hit[from]) {
                pieces += Piece(text.substring(from, i), hit[from])
                from = i
            }
        }
        return pieces
    }
}

data class Piece(val text: String, val match: Boolean)
