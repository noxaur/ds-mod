package io.github.noxaur.dragen.core.feature.menus.petsmenu

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.container.SlotMark
import io.github.noxaur.dragen.core.feature.ContainerReader
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area
import io.github.noxaur.dragen.core.game.Pet
import io.github.noxaur.dragen.core.game.PetCard
import io.github.noxaur.dragen.core.text.Role

class PetsMenu(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "pets-menu", Area.ANYWHERE, Store.INVENTORY, "Outline your active pet in the Pets menu.", slots = ::slots)

    private val container = ContainerReader(feature)

    private var marks = emptyMap<Int, SlotMark>()

    private fun slots(now: Long): Map<Int, SlotMark> {
        if (!container.changed()) return marks
        val open = feature.container
        marks = if (open.title?.plain != PetCard.MENU) emptyMap() else open.menuSlots
            .filter { slot -> open.slots[slot]?.let { it.name?.plain?.let(Pet::nameOf) != null && PetCard.ACTIVE in it.cleanLore } == true }
            .associateWith { SlotMark(Role.GOOD, outline = true) }
        return marks
    }
}
