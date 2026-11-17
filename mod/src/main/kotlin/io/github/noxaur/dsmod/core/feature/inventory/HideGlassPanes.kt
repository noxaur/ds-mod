package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.container.Filler
import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.text.text
import kotlinx.serialization.Serializable

/** Whether the player has been told that filler panes are hidden. */
@Serializable
data class HideGlassPanesTold(val told: Boolean = false)

/** Hides the [Filler] panes in a menu, so the items that do something stand out. */
class HideGlassPanes(config: DsModConfig) :
    Feature(config, "hide-glass-panes", Area.ANYWHERE, Store.INVENTORY, "Hide the blank glass panes that pad out menus.", formerly = listOf("hideFiller")) {
    private val told = saved(HideGlassPanesTold())
    private var marks = emptyMap<Int, SlotMark>()
    private var changed = false

    init {
        on<ContainerOpened> { _, _ -> changed = true }
        on<SlotChanged> { slot, _ ->
            changed = true
            if (told.value.told || slot.item?.let(::filler) != true) return@on
            told.value = HideGlassPanesTold(told = true)
            notice(text { body("Hid the blank glass panes in this menu. Turn off "); value("Hide Glass Panes"); body(" in /dsmod to see them.") })
        }
    }

    override fun slots(now: Long): Map<Int, SlotMark> {
        if (!changed) return marks
        changed = false
        val next = HashMap<Int, SlotMark>()
        container.slots.forEachIndexed { slot, item -> if (item != null && filler(item)) next[slot] = SlotMark.HIDE }
        marks = next
        return marks
    }

    private fun filler(item: Item) = Filler.of(item.id, item.name?.plain)
}
