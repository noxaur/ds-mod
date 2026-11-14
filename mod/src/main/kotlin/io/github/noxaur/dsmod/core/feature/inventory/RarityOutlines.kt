package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.text.Role
import kotlin.reflect.KMutableProperty0

/** Frames each item's slot in the color the server gives that rarity's item names. */
class RarityOutlines(toggle: KMutableProperty0<Boolean>) : Feature("rarity-outlines", toggle, Area.ANYWHERE) {
    private var marks = emptyMap<Int, SlotMark>()
    private var changed = false

    private val byRarity = listOf(Role.COMMON, Role.UNCOMMON, Role.RARE, Role.EPIC, Role.LEGENDARY, Role.MYTHIC, Role.DIVINE, Role.SPECIAL)
        .associate { it.name to SlotMark(it, border = true) }

    init {
        on<ContainerOpened> { _, _ -> changed = true }
        on<SlotChanged> { _, _ -> changed = true }
    }

    override fun slots(now: Long): Map<Int, SlotMark> {
        if (!changed) return marks
        changed = false
        val next = HashMap<Int, SlotMark>()
        container.slots.forEachIndexed { slot, item ->
            val rarity = item?.rarity ?: return@forEachIndexed
            val mark = byRarity[rarity]
            if (mark == null) count("unparsed") else next[slot] = mark
        }
        marks = next
        return marks
    }
}
