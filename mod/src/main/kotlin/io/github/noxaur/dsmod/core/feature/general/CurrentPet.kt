package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.EntitySeen
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.LeftServer
import io.github.noxaur.dsmod.core.game.Pet
import io.github.noxaur.dsmod.core.game.PetCard
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.game.SlotChanged

class CurrentPet(
    private val feature: Feature,
    private val changed: (before: Pet?) -> Unit,
    private val menuSlot: (event: SlotChanged, card: PetCard?, now: Long) -> Unit = { _, _, _ -> },
    private val headSeen: (name: String) -> Unit = {},
) {
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
        feature.on<SlotChanged> { event, now ->
            if (feature.container.title?.plain != PetCard.MENU) return@on
            val card = event.item?.let(PetCard::of)
            if (card != null) this.card = card
            menuSlot(event, card, now)
        }
        feature.on<LeftServer> { _, _ -> set(null) }
        feature.readEntities(Pet.STANDS)
        feature.on<EntitySeen> { seen, _ ->
            val (name, head) = Pet.worn(seen) ?: return@on
            if (heads.put(name, head) != head) headSeen(name)
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
        val before = pet
        pet = next
        changed(before)
    }

    private companion object {
        const val SIDEBAR = "Pet:"
        const val NONE = "None"
    }
}
