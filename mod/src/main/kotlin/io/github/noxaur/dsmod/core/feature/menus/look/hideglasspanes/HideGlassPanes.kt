package io.github.noxaur.dsmod.core.feature.menus.look.hideglasspanes

import io.github.noxaur.dsmod.core.container.Filler
import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.text.text
import kotlinx.serialization.Serializable
import io.github.noxaur.dsmod.core.feature.SlotMarks

@Serializable
data class HideGlassPanesTold(val told: Boolean = false)

class HideGlassPanes(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "hide-glass-panes", Area.ANYWHERE, Store.INVENTORY, "Hide the blank glass panes that pad out menus.", formerly = listOf("hideFiller"), slots = ::slots)

    private val told = feature.saved(HideGlassPanesTold())
    private val marks = SlotMarks(feature) { _, item, _ -> SlotMark.HIDE.takeIf { filler(item) } }

    init {
        feature.on<SlotChanged> { slot, _ ->
            if (told.value.told || slot.item?.let(::filler) != true) return@on
            told.value = HideGlassPanesTold(told = true)
            feature.notice(text { body("Hid the blank glass panes in this menu. Turn off "); value("Hide Glass Panes"); body(" in /dsmod to see them.") })
        }
    }

    fun slots(now: Long): Map<Int, SlotMark> = marks.marks(now)

    private fun filler(item: Item) = Filler.of(item.id, item.name?.plain)
}
