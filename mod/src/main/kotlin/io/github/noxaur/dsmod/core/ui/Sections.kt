package io.github.noxaur.dsmod.core.ui

import kotlinx.serialization.Serializable

@Serializable
data class Section(val from: Int = 0, val to: Int = 0)

/** @keep A drag is worked out from the sections as they were when the button went down, not from the last frame, so a section
 * that merged on the way comes apart again when the mouse moves back. */
object Sections {
    const val MIN_WIDTH = 5
    const val MERGE = 3

    enum class Edge { START, END }

    fun contains(sections: List<Section>, value: Int) = at(sections, value) >= 0

    fun at(sections: List<Section>, value: Int) = sections.indexOfFirst { value >= it.from && value <= it.to }

    fun merged(sections: List<Section>): List<Section> {
        val result = ArrayList<Section>(sections.size)
        for (section in sections.sortedBy { it.from }) {
            val last = result.lastOrNull()
            if (last != null && section.from - last.to <= MERGE) result[result.size - 1] = Section(last.from, maxOf(last.to, section.to)) else result += section
        }
        return result
    }

    fun add(sections: List<Section>, value: Int, width: Int, max: Int): List<Section> {
        val low = sections.lastOrNull { it.to < value }?.let { it.to + MERGE + 1 } ?: 0
        val high = sections.firstOrNull { it.from > value }?.let { it.from - MERGE - 1 } ?: max
        if (at(sections, value) >= 0 || high - low < MIN_WIDTH) return sections
        val size = width.coerceIn(MIN_WIDTH, high - low)
        val from = (value - size / 2).coerceIn(low, high - size)
        return merged(sections + Section(from, from + size))
    }

    fun draw(sections: List<Section>, anchor: Int, value: Int, max: Int): List<Section> {
        var from = minOf(anchor, value).coerceIn(0, max)
        var to = maxOf(anchor, value).coerceIn(0, max)
        if (to - from < MIN_WIDTH) {
            if (value >= anchor) to = from + MIN_WIDTH else from = to - MIN_WIDTH
            if (to > max) from = max - MIN_WIDTH.also { to = max }
            if (from < 0) to = MIN_WIDTH.also { from = 0 }
        }
        return merged(sections + Section(from, to))
    }

    fun resize(sections: List<Section>, index: Int, edge: Edge, value: Int, max: Int): List<Section> {
        val section = sections[index]
        val changed = when (edge) {
            Edge.START -> section.copy(from = value.coerceIn(0, section.to - MIN_WIDTH))
            Edge.END -> section.copy(to = value.coerceIn(section.from + MIN_WIDTH, max))
        }
        return replaced(sections, index, changed)
    }

    fun move(sections: List<Section>, index: Int, from: Int, max: Int): List<Section> {
        val size = sections[index].to - sections[index].from
        val start = from.coerceIn(0, max - size)
        return replaced(sections, index, Section(start, start + size))
    }

    private fun replaced(sections: List<Section>, index: Int, section: Section) = merged(sections.toMutableList().also { it[index] = section })

    fun remove(sections: List<Section>, index: Int) = sections.filterIndexed { i, _ -> i != index }
}
