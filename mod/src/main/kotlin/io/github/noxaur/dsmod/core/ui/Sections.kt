package io.github.noxaur.dsmod.core.ui

import com.google.gson.annotations.Expose

/** One stretch of a scale that is switched on, from [from] up to and including [to]. */
data class Section(@Expose val from: Int = 0, @Expose val to: Int = 0)

/**
 * The stretches of a scale from 0 to `max` that are switched on, as a range slider edits them. Every list these
 * return is sorted, and its sections are at least [MIN_WIDTH] long and [GAP] apart, so two never touch and each
 * stays wide enough to grab.
 */
object Sections {
    const val MIN_WIDTH = 5
    const val GAP = 5

    enum class Edge { START, END }

    fun contains(sections: List<Section>, value: Int) = sections.any { value >= it.from && value <= it.to }

    /** How much of the scale is on. */
    fun covered(sections: List<Section>) = sections.sumOf { it.to - it.from }

    /** The index of the section that holds [value], or -1. */
    fun at(sections: List<Section>, value: Int) = sections.indexOfFirst { value >= it.from && value <= it.to }

    /** [sections] with a new one [width] long around [value], cut to the room its neighbors leave, or unchanged when there is none. */
    fun add(sections: List<Section>, value: Int, width: Int, max: Int): List<Section> {
        val low = (sections.lastOrNull { it.to < value }?.let { it.to + GAP } ?: 0)
        val high = (sections.firstOrNull { it.from > value }?.let { it.from - GAP } ?: max)
        if (at(sections, value) >= 0 || high - low < MIN_WIDTH) return sections
        val size = width.coerceIn(MIN_WIDTH, high - low)
        val from = (value - size / 2).coerceIn(low, high - size)
        return (sections + Section(from, from + size)).sortedBy { it.from }
    }

    /** [sections] with one [edge] of the section at [index] dragged to [value], stopped by its neighbor, the scale's end and its own other edge. */
    fun resize(sections: List<Section>, index: Int, edge: Edge, value: Int, max: Int): List<Section> {
        val section = sections[index]
        val changed = when (edge) {
            Edge.START -> section.copy(from = value.coerceIn(sections.getOrNull(index - 1)?.let { it.to + GAP } ?: 0, section.to - MIN_WIDTH))
            Edge.END -> section.copy(to = value.coerceIn(section.from + MIN_WIDTH, sections.getOrNull(index + 1)?.let { it.from - GAP } ?: max))
        }
        return sections.toMutableList().also { it[index] = changed }
    }

    /** [sections] with the section at [index] slid so it starts at [from], stopped by its neighbors and the scale's ends. */
    fun move(sections: List<Section>, index: Int, from: Int, max: Int): List<Section> {
        val section = sections[index]
        val size = section.to - section.from
        val low = sections.getOrNull(index - 1)?.let { it.to + GAP } ?: 0
        val high = (sections.getOrNull(index + 1)?.let { it.from - GAP } ?: max) - size
        val start = from.coerceIn(low, maxOf(low, high))
        return sections.toMutableList().also { it[index] = Section(start, start + size) }
    }

    fun remove(sections: List<Section>, index: Int) = sections.filterIndexed { i, _ -> i != index }
}
