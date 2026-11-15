package io.github.noxaur.dsmod.core.ui

import io.github.noxaur.dsmod.core.config.Setting
import io.github.noxaur.dsmod.core.config.SettingGroup
import io.github.noxaur.dsmod.core.config.SettingSection

/**
 * The rows of [group], in section [section], that a search found. A row is found by itself or by a row under it,
 * and [open] names the rows to open so that each hit under them shows.
 */
data class SettingHit(val section: Int, val group: SettingGroup, val rows: List<Setting>, val open: Set<String>)

/**
 * The settings screen's search. A row matches when every word of the query is in its name, its description, a
 * choice's values, its group's heading or switch, or the names of the rows it sits under, ignoring case, as
 * MoulConfig matches. A group's heading thereby finds all its rows.
 */
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
                val rows = group.rows.filter { row -> found(row, heading, words, open) != Found.NO }
                if (rows.isNotEmpty()) hits += SettingHit(i, group, rows, open)
            }
        }
        return hits
    }

    private enum class Found { NO, ABOVE, SELF }

    /**
     * Whether [row] or a row under it matches, and whether by its own words or only by the headings [above] it. A row
     * that holds a match of its own below it goes into [open], so a heading that finds a whole group opens nothing.
     */
    private fun found(row: Setting, above: String, words: List<String>, open: MutableSet<String>): Found {
        var below = false
        for (child in row.children) if (found(child, "$above ${row.name.lowercase()}", words, open) == Found.SELF) below = true
        if (below) open += row.id
        val text = "$above ${row.searchText}"
        return when {
            below || words.all { it in text } && !words.all { it in above } -> Found.SELF
            words.all { it in text } -> Found.ABOVE
            else -> Found.NO
        }
    }

    /** How many rows [hits] found in each of [size] sections. */
    fun counts(hits: List<SettingHit>, size: Int) = IntArray(size).also { counts -> for (hit in hits) counts[hit.section] += hit.rows.size }

    /**
     * [text] cut into the stretches that match one of [words] and those between, in order, ignoring case. Every
     * place a word occurs is a match, and matches that touch or overlap are one, so a screen paints each hit once.
     */
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

/** A stretch of a label, and whether a search matched it. */
data class Piece(val text: String, val match: Boolean)
