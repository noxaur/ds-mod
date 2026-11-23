package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerState

class WardrobeKeys(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "wardrobe-keys", Area.ANYWHERE, Store.INVENTORY, "Press 1 to 9 in the wardrobe to click that slot.", slots = ::slots)

    private val keyList = (1..SLOTS).map { n -> feature.keys("wardrobeKey$n", "Slot $n", "Pick the key that clicks wardrobe slot $n.", MenuKeys.KEY_0 + n) }

    init {
        feature.fold("wardrobeKeys", "Keys", "Pick the key for each wardrobe slot.", *keyList.toTypedArray())
    }

    val keys = MenuKeys(feature, TITLE, keyList, ::button)

    private fun button(index: Int): Int? = (BUTTON_ROW * ContainerState.ROW_SLOTS + index).takeIf { slot ->
        slot in feature.container.menuSlots && feature.container.slots[slot]?.name?.plain?.let(BUTTON::matches) == true
    }

    private fun slots(now: Long): Map<Int, SlotMark> = keys.marks

    companion object {
        const val TITLE = "Wardrobe"
        const val SLOTS = 9
        private const val BUTTON_ROW = 4
        private val BUTTON = Regex("Slot \\d+")
    }
}
