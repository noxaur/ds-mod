package io.github.noxaur.dragen.core.feature

import io.github.noxaur.dragen.core.container.SlotMark
import io.github.noxaur.dragen.core.game.Item

fun interface Marker {
    fun mark(slot: Int, item: Item, now: Long): SlotMark?
}

class SlotMarks(private val feature: Feature, private val marker: Marker) {
    private val container = ContainerReader(feature)
    private var stale = true
    private var until = Long.MAX_VALUE
    private var marks = emptyMap<Int, SlotMark>()

    fun invalidate() {
        stale = true
    }

    fun invalidateAt(time: Long) {
        until = minOf(until, time)
    }

    fun marks(now: Long): Map<Int, SlotMark> {
        if (!container.changed() && !stale && now < until) return marks
        stale = false
        until = Long.MAX_VALUE
        val next = HashMap<Int, SlotMark>()
        val slots = feature.container.slots
        for (slot in slots.indices) {
            val item = slots[slot] ?: continue
            marker.mark(slot, item, now)?.let { next[slot] = it }
        }
        marks = next
        return marks
    }
}
