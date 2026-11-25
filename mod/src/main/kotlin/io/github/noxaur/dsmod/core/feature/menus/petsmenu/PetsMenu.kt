package io.github.noxaur.dsmod.core.feature.menus.petsmenu

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.feature.ContainerReader
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.PetCard
import io.github.noxaur.dsmod.core.text.Role

class PetsMenu(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "pets-menu", Area.ANYWHERE, Store.INVENTORY, "Outline your active pet in the Pets menu.", slots = ::slots)

    private val container = ContainerReader(feature)

    private var marks = emptyMap<Int, SlotMark>()

    private fun slots(now: Long): Map<Int, SlotMark> {
        if (!container.changed()) return marks
        val open = feature.container
        marks = if (open.title?.plain != PetCard.MENU) emptyMap() else open.menuSlots
            .filter { open.slots[it]?.cleanLore?.contains(PetCard.ACTIVE) == true }
            .associateWith { SlotMark(Role.GOOD, outline = true) }
        return marks
    }
}
