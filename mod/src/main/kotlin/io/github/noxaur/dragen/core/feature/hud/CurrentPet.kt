package io.github.noxaur.dragen.core.feature.hud

import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.game.EntityFilter
import io.github.noxaur.dragen.core.game.EntitySeen
import io.github.noxaur.dragen.core.game.Item
import io.github.noxaur.dragen.core.game.LeftServer
import io.github.noxaur.dragen.core.game.Pet
import io.github.noxaur.dragen.core.game.PetCard
import io.github.noxaur.dragen.core.game.PetItem
import io.github.noxaur.dragen.core.game.ServerText
import io.github.noxaur.dragen.core.game.Sidebar
import io.github.noxaur.dragen.core.game.SlotChanged
import io.github.noxaur.dragen.core.text.Role

class CurrentPet(private val feature: Feature, private val changed: () -> Unit) {
    var pet: Pet? = null
        private set

    var card: PetCard? by feature.session { null }
        private set

    private val heads = HashMap<String, Item>()

    fun head(name: String): Item? = heads[name]

    init {
        feature.on<Sidebar> { event, _ ->
            if (event.lines.all { it.plain.isBlank() }) return@on
            event.lines.firstOrNull { it.plain.trimStart().startsWith(SIDEBAR) }?.let { read(it, SIDEBAR) }
        }
        feature.on<SlotChanged> { event, _ ->
            if (feature.container.title?.plain != PetCard.MENU) return@on
            val card = event.item?.let(::cardOf) ?: return@on
            this.card = card
            changed()
        }
        feature.on<LeftServer> { _, _ -> set(null) }
        feature.readEntities(STANDS)
        feature.on<EntitySeen> { seen, _ ->
            val (name, head) = worn(seen) ?: return@on
            if (heads.put(name, head) != head) changed()
        }
    }

    fun read(line: ServerText, prefix: String) {
        val value = line.plain.trim().removePrefix(prefix).trim()
        if (value == NONE) return set(null)
        val read = Pet.of(line, value)
        if (read == null) feature.unparsed(line.plain)
        set(read)
    }

    fun set(next: Pet?) {
        if (next == pet) return
        pet = next
        changed()
    }

    private companion object {
        const val SIDEBAR = "Pet:"
        const val NONE = "None"
    }
}

private val STANDS = EntityFilter(setOf("minecraft:armor_stand"), "[Lvl ")

/** @keep dragsim.co floats each player's pet beside them as an armor stand named like `[Lvl 100] Black Cat` that
 * wears the pet's head, so a pet of another player's may show one of the same name */
private fun worn(seen: EntitySeen): Pair<String, Item>? {
    val head = seen.head?.takeIf { it.id == "minecraft:player_head" } ?: return null
    return Pet.nameOf(seen.name.plain)?.let { it to head }
}

private const val HELD = "Held Item"

private fun cardOf(item: Item): PetCard? {
    val lore = item.cleanLore
    if (PetCard.ACTIVE !in lore) return null
    val pet = item.name?.let(Pet::of) ?: return null
    val heldAt = lore.indexOf(HELD)
    val held = item.lore.getOrNull(heldAt + 1)?.takeIf { heldAt >= 0 && lore[heldAt + 1].isNotEmpty() }?.let {
        PetItem(lore[heldAt + 1], Role.rarity(it.spans.lastOrNull { span -> span.text.isNotBlank() }?.color))
    }
    return PetCard(pet, item, held)
}
