package io.github.noxaur.dsmod.core.feature.general.itemoverview

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.KeyBind
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.overview.Copy
import io.github.noxaur.dsmod.core.overview.Owned
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.feature.ContainerReader

class ItemOverview(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "item-overview", Area.ANYWHERE, Store.GENERAL, "List every item you own in one screen with /dsmod items.", slots = ::slots)

    val key = feature.keys("itemOverviewKey", "Key", "Pick a key that opens the item overview.", KeyBind.NONE)
    val highlight = feature.number("itemOverviewHighlight", "Highlight Time", "Set how long a clicked item stays outlined in its menu.", 15f, 5f..60f, step = 5f, unit = "s")

    private class Wanted(val key: Owned.Key, val until: Long)

    private var wanted by feature.session<Wanted?> { null }
    private var marks by feature.session { emptyMap<Int, SlotMark>() }
    private var stale = false
    private val reader = ContainerReader(feature)

    val copies: List<Copy> get() = feature.player.containers.map { (id, seen) -> Copy(id, seen.title, seen.items.map { it?.let(Owned::of) }) }

    fun find(key: Owned.Key, now: Long) {
        wanted = Wanted(key, now + (highlight.value * 1000).toLong())
        stale = true
        feature.redraw()
    }

    private fun slots(now: Long): Map<Int, SlotMark> {
        if (reader.changed() || stale) {
            stale = false
            val shelf = feature.player.shelf
            val items = shelf?.let { feature.player.containers[it.id]?.items }
            val key = wanted?.key
            marks = if (shelf == null || items == null || key == null) emptyMap()
            else items.indices.filter { items[it]?.let(Owned::of)?.key == key }.associate { shelf.slots.first + it to MARK }
        }
        if ((wanted?.until ?: Long.MAX_VALUE) <= now) {
            wanted = null
            marks = emptyMap()
        }
        return marks
    }

    private companion object {
        val MARK = SlotMark(Role.ACTION, outline = true)
    }
}
