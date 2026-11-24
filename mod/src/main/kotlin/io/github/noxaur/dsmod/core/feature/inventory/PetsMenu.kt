package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.feature.general.ContainerReader
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.Pet
import io.github.noxaur.dsmod.core.game.PetCard
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.text

class PetsMenu(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "pets-menu", Area.ANYWHERE, Store.INVENTORY, "Mark your active pet, and press 1 to 0 to click a pet.", slots = ::slots)

    val markActive = feature.switch("markActivePet", "Mark Active Pet", "Outline the pet you have out.", default = true)

    private val keyList = (1..PETS).map { n ->
        feature.keys("petKey$n", "Pet $n", "Pick the key that clicks pet $n in the Pets menu.", MenuKeys.KEY_0 + n % 10)
    }

    init {
        feature.fold("petKeys", "Keys", "Pick the key for each pet, counted from the top left.", *keyList.toTypedArray())
    }

    val keys = MenuKeys(feature, PetCard.MENU, keyList, waitMillis = 0) { pets().getOrNull(it) }

    private val container = ContainerReader(feature)

    private data class Drawn(val mark: Boolean, val keys: List<Int>, val held: Map<Int, SlotMark>)

    private var drawn: Drawn? = null
    private var marks = emptyMap<Int, SlotMark>()

    private fun pets(): List<Int> {
        if (feature.container.title?.plain != PetCard.MENU) return emptyList()
        val slots = feature.container.slots
        return feature.container.menuSlots.filter { slot -> slots[slot]?.name?.plain?.let(Pet::nameOf) != null }
    }

    private fun slots(now: Long): Map<Int, SlotMark> {
        val next = Drawn(markActive.value, keyList.map { it.value }, keys.marks)
        if (!container.changed() && next == drawn) return marks
        drawn = next
        val held = next.held.keys
        marks = pets().withIndex().mapNotNull { (n, slot) ->
            val label = next.keys.getOrNull(n)?.takeIf { next.keys.indexOf(it) == n }?.let(::keyLabel)?.let { text { value(it) } }
            val active = next.mark && feature.container.slots[slot]?.cleanLore?.contains(PetCard.ACTIVE) == true
            val role = if (slot in held) Role.ACTION else if (active) Role.GOOD else Role.MUTED
            if (label == null && slot !in held && !active) null else slot to SlotMark(role, label, outline = slot in held || active)
        }.toMap()
        return marks
    }

    companion object {
        private const val PETS = 10

        private fun keyLabel(bound: Int): String? = if (bound in '!'.code..'`'.code) bound.toChar().toString() else null
    }
}
