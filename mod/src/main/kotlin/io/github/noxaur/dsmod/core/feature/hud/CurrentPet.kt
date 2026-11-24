package io.github.noxaur.dsmod.core.feature.hud

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.EntityFilter
import io.github.noxaur.dsmod.core.game.EntitySeen
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.LeftServer
import io.github.noxaur.dsmod.core.game.Pet
import io.github.noxaur.dsmod.core.game.PetCard
import io.github.noxaur.dsmod.core.game.PetItem
import io.github.noxaur.dsmod.core.game.PetProgress
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.number

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
            val card = event.item?.let(::cardOf)
            if (card != null) this.card = card
            menuSlot(event, card, now)
        }
        feature.on<LeftServer> { _, _ -> set(null) }
        feature.readEntities(STANDS)
        feature.on<EntitySeen> { seen, _ ->
            val (name, head) = worn(seen) ?: return@on
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

private val STANDS = EntityFilter(setOf("minecraft:armor_stand"), "[Lvl ")

/** @keep dragsim.co floats each player's pet beside them as an armor stand named like `[Lvl 100] Black Cat` that
 * wears the pet's head, so a pet of another player's may show one of the same name */
private fun worn(seen: EntitySeen): Pair<String, Item>? {
    val head = seen.head?.takeIf { it.id == "minecraft:player_head" } ?: return null
    return Pet.nameOf(seen.name.plain)?.let { it to head }
}

private const val HELD = "Held Item"
private const val MAX_LEVEL = "MAX LEVEL"
private val LEVEL = Regex("""LEVEL \d+""")
private val XP = Regex("""([\d,.]+)\s*/\s*([\d,.]+)""")
private val PERCENT = Regex("""(\d+(?:\.\d+)?)%""")

/** @keep Only a pet at its max level was seen, so the lines between its level and the click hint are read leniently:
 * an `xp/needed` pair, or else a percent */
private fun cardOf(item: Item): PetCard? {
    val lore = item.cleanLore
    if (PetCard.ACTIVE !in lore) return null
    val pet = item.name?.let(Pet::of) ?: return null
    val key = item.string("extra", "item_uuid", "uuid") ?: pet.name
    val category = lore.firstNotNullOfOrNull { PetCard.FOOTER.matchEntire(it)?.groupValues?.get(2)?.trim() }
    val heldAt = lore.indexOf(HELD)
    val held = item.lore.getOrNull(heldAt + 1)?.takeIf { heldAt >= 0 && lore[heldAt + 1].isNotEmpty() }?.let {
        PetItem(lore[heldAt + 1], Role.rarity(it.spans.lastOrNull { span -> span.text.isNotBlank() }?.color))
    }
    val levelAt = lore.indexOfFirst { LEVEL.matches(it) }
    if (levelAt < 0) return PetCard(pet, item, held, null, key = key, category = category)
    val lines = lore.drop(levelAt + 1)
        .takeWhile { it != "ACTIVE" && !it.startsWith("Click") }
        .filter { it.isNotEmpty() }
    if (MAX_LEVEL in lines) return PetCard(pet, item, held, PetProgress.MAX, key = key, category = category)
    val xp = lines.firstNotNullOfOrNull { XP.find(it) }?.destructured?.let { (have, need) ->
        val a = number(have)
        val b = number(need)
        if (a != null && b != null && b > 0) PetProgress((a / b).toFloat().coerceIn(0f, 1f), a, b) else null
    }
    val percent = lines.firstNotNullOfOrNull { PERCENT.find(it) }?.groupValues?.get(1)?.let(::number)?.toFloat()
        ?.let { PetProgress((it / 100f).coerceIn(0f, 1f)) }
    val progress = xp ?: percent
    val unread = if (progress == null && lines.isNotEmpty()) lines.joinToString(" / ") else null
    return PetCard(pet, item, held, progress, unread, key, category)
}
