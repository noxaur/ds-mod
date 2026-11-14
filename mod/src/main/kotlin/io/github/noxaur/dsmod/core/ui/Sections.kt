package io.github.noxaur.dsmod.core.ui

import com.google.gson.annotations.Expose

/** One stretch of a scale that is switched on, from [from] up to and including [to]. */
data class Section(@Expose val from: Int = 0, @Expose val to: Int = 0)

/**
 * The stretches of a scale from 0 to `max` that are switched on, as a range slider edits them. Every list these
 * return is sorted, with sections at least [MIN_WIDTH] long. Two sections that overlap or come within [MERGE]
 * of each other become one, so dragging a section into its neighbor joins them.
 *
 * A drag is worked out from the sections as they were when the button went down, not from the last frame. A
 * section that merged on the way then comes apart again when the mouse moves back.
 */
object Sections {
    const val MIN_WIDTH = 5
    const val MERGE = 3

    enum class Edge { START, END }

    fun contains(sections: List<Section>, value: Int) = at(sections, value) >= 0

    /** How much of the scale is on. */
    fun covered(sections: List<Section>) = sections.sumOf { it.to - it.from }

    /** The index of the section that holds [value], or -1. */
    fun at(sections: List<Section>, value: Int) = sections.indexOfFirst { value >= it.from && value <= it.to }

    /** [sections] sorted, with every two that overlap or lie within [MERGE] joined. */
    fun merged(sections: List<Section>): List<Section> {
        val result = ArrayList<Section>(sections.size)
        for (section in sections.sortedBy { it.from }) {
            val last = result.lastOrNull()
            if (last != null && section.from - last.to <= MERGE) result[result.size - 1] = Section(last.from, maxOf(last.to, section.to)) else result += section
        }
        return result
    }

    /** [sections] with a new one [width] long around [value], for a click. It is cut to the room its neighbors leave, and a click with no room changes nothing. */
    fun add(sections: List<Section>, value: Int, width: Int, max: Int): List<Section> {
        val low = sections.lastOrNull { it.to < value }?.let { it.to + MERGE + 1 } ?: 0
        val high = sections.firstOrNull { it.from > value }?.let { it.from - MERGE - 1 } ?: max
        if (at(sections, value) >= 0 || high - low < MIN_WIDTH) return sections
        val size = width.coerceIn(MIN_WIDTH, high - low)
        val from = (value - size / 2).coerceIn(low, high - size)
        return merged(sections + Section(from, from + size))
    }

    /** [sections] with a new one drawn from [anchor], where the button went down, to [value], where the mouse is now. */
    fun draw(sections: List<Section>, anchor: Int, value: Int, max: Int): List<Section> {
        var from = minOf(anchor, value).coerceIn(0, max)
        var to = maxOf(anchor, value).coerceIn(0, max)
        // Too short to grab, it grows the way the mouse is going, or back from the scale's end.
        if (to - from < MIN_WIDTH) {
            if (value >= anchor) to = from + MIN_WIDTH else from = to - MIN_WIDTH
            if (to > max) from = max - MIN_WIDTH.also { to = max }
            if (from < 0) to = MIN_WIDTH.also { from = 0 }
        }
        return merged(sections + Section(from, to))
    }

    /** [sections] with one [edge] of the section at [index] dragged to [value]. The edge stops at the scale's end and at its own other edge. */
    fun resize(sections: List<Section>, index: Int, edge: Edge, value: Int, max: Int): List<Section> {
        val section = sections[index]
        val changed = when (edge) {
            Edge.START -> section.copy(from = value.coerceIn(0, section.to - MIN_WIDTH))
            Edge.END -> section.copy(to = value.coerceIn(section.from + MIN_WIDTH, max))
        }
        return merged(sections.toMutableList().also { it[index] = changed })
    }

    /** [sections] with the section at [index] slid to start at [from], kept on the scale. */
    fun move(sections: List<Section>, index: Int, from: Int, max: Int): List<Section> {
        val size = sections[index].to - sections[index].from
        val start = from.coerceIn(0, max - size)
        return merged(sections.toMutableList().also { it[index] = Section(start, start + size) })
    }

    fun remove(sections: List<Section>, index: Int) = sections.filterIndexed { i, _ -> i != index }
}
