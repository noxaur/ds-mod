package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.KeyBind
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.ContainerState
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.overview.Copy
import io.github.noxaur.dsmod.core.overview.Owned
import io.github.noxaur.dsmod.core.overview.Shelf
import io.github.noxaur.dsmod.core.text.Role

class ItemOverview(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "item-overview", Area.ANYWHERE, Store.GENERAL, "List every item you own in one screen with /dsmod items.", slots = ::slots)

    val key = feature.keys("itemOverviewKey", "Key", "Pick a key that opens the item overview.", KeyBind.NONE)
    val highlight = feature.number("itemOverviewHighlight", "Highlight Time", "Set how long a clicked item stays outlined in its menu.", 15f, 5f..60f, step = 5f, unit = "s")

    private class Wanted(val key: Owned.Key, val until: Long)

    private class Kept(val shelf: Shelf, val items: Array<Owned?>, var at: Long)

    /** @keep A copy lasts the session. dragsim.co saves the profile again soon enough, and a copy kept on disk could
     * outlive a switch to another profile. Each slot is read as it arrives, so a menu costs one item per change. */
    private var kept by feature.session { LinkedHashMap<String, Kept>() }
    private var open by feature.session<Kept?> { null }
    private var wanted by feature.session<Wanted?> { null }
    private var marks by feature.session { emptyMap<Int, SlotMark>() }
    private var stale = false

    init {
        feature.on<ContainerOpened> { opened, now ->
            val shelf = Shelf.of(opened.title?.plain)?.takeIf { it.slots.last < opened.size - ContainerState.PLAYER_SLOTS }
            open = shelf?.let { Kept(it, arrayOfNulls(it.slots.count()), now) }?.also { kept[it.shelf.id] = it }
            stale = true
        }
        feature.on<SlotChanged> { changed, now ->
            val menu = open ?: return@on
            val i = changed.slot - menu.shelf.slots.first
            if (i !in menu.items.indices) return@on
            menu.items[i] = changed.item?.let(Owned::of)
            menu.at = now
            stale = true
        }
    }

    /** @keep Each Storage page and the Ender Chest as the player last saw them this session, with when. */
    val copies: List<Pair<Copy, Long>> get() = kept.values.map { Copy(it.shelf.id, it.shelf.title, it.items.toList()) to it.at }

    fun find(key: Owned.Key, now: Long) {
        wanted = Wanted(key, now + (highlight.value * 1000).toLong())
        stale = true
        feature.redraw()
    }

    private fun slots(now: Long): Map<Int, SlotMark> {
        if (stale) {
            stale = false
            val menu = open
            val key = wanted?.key
            marks = if (menu == null || key == null) emptyMap() else menu.items.indices.filter { menu.items[it]?.key == key }.associate { menu.shelf.slots.first + it to MARK }
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
