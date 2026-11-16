package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.text.Role
import kotlin.reflect.KMutableProperty0

/** Marks each item's slot in the color the server gives that rarity's item names, unless [markCommon] is off for Common. */
class ItemRarity(toggle: KMutableProperty0<Boolean>, private val markCommon: KMutableProperty0<Boolean>) :
    Feature("item-rarity", toggle, Area.ANYWHERE) {
    private var marks = emptyMap<Int, SlotMark>()
    private var changed = false
    private var common = markCommon.get()

    private val byRarity = Role.RARITIES.associate { it.name to SlotMark(it, rarity = true) }

    init {
        on<ContainerOpened> { _, _ -> changed = true }
        on<SlotChanged> { _, _ -> changed = true }
    }

    override fun slots(now: Long): Map<Int, SlotMark> {
        if (common != markCommon.get()) {
            common = !common
            changed = true
        }
        if (!changed) return marks
        changed = false
        val next = HashMap<Int, SlotMark>()
        container.slots.forEachIndexed { slot, item ->
            val rarity = item?.rarity ?: return@forEachIndexed
            val mark = byRarity[rarity]
            when {
                mark == null -> unparsed(rarity)
                mark.role != Role.COMMON || common -> next[slot] = mark
            }
        }
        marks = next
        return marks
    }
}
