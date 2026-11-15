package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.PetNear
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.text.Role
import kotlin.reflect.KMutableProperty0

/** A piece the display can show: a piece of armor and the slot of the player's inventory that holds it, or the pet. */
enum class EquipmentPiece(private val label: String, val slot: Int) {
    HELMET("Helmet", 5),
    CHESTPLATE("Chestplate", 6),
    LEGGINGS("Leggings", 7),
    BOOTS("Boots", 8),
    PET("Pet", -1),
    ;

    override fun toString() = label
}

/** How the icons line up. */
enum class EquipmentLayout(private val label: String) {
    ROW("Row"),
    COLUMN("Column"),
    ;

    override fun toString() = label
}

/** How the pet shows: its head among the armor, or its name on a line of its own. */
enum class PetLook(private val label: String) {
    ICON("Icon"),
    NAME("Name"),
    ;

    override fun toString() = label
}

/**
 * Shows the [pieces] the player picked, in their order, as icons with no header, the way the equipment menu shows
 * them. An empty cell stands for a piece not worn.
 *
 * The armor comes from the player's own inventory, and the last seen is kept while another menu is open, since that
 * menu does not hold the armor. The pet comes from the sidebar's line `Pet: [Lvl 100] Ender Dragon`, and its icon is
 * the head of the nearest pet whose name is the sidebar's: dragsim.co floats each pet beside its owner. Until that
 * head is seen, or with [petLook] on Name, the pet is a line under the icons, as the sidebar writes it.
 */
class EquipmentDisplay(
    toggle: KMutableProperty0<Boolean>,
    private val layout: KMutableProperty0<EquipmentLayout>,
    private val pieces: KMutableProperty0<MutableList<EquipmentPiece>>,
    private val petLook: KMutableProperty0<PetLook>,
) : Feature("equipment-display", toggle, Area.ANYWHERE) {
    private class Pet(val name: String, val level: Long, val rarity: Role)

    private var worn = emptyMap<EquipmentPiece, Item>()
    private var pet: Pet? = null

    // The head of the last pet seen near the player, by the pet's name without its level, which goes up in play.
    private var head: Pair<String, Item>? = null

    private var shownLayout = layout.get()
    private var shownPieces = pieces.get().toList()
    private var shownLook = petLook.get()
    private var block: HudBlock? = null

    // Whether the armor may have changed since it was last read. A refresh with nothing new then costs a few compares.
    private var stale = true

    init {
        on<ContainerOpened> { event, _ -> if (event.id == INVENTORY) stale = true }
        on<SlotChanged> { event, _ -> if (container.id == INVENTORY && ARMOR.any { it.slot == event.slot }) stale = true }
        // Also run when the feature is switched off, so it reads the armor again when it comes back.
        on<WorldChanged> { _, _ -> stale = true }
        on<Sidebar> { event, _ ->
            // Between worlds the sidebar is empty until the new one arrives, and the pet is still out.
            if (event.lines.all { it.plain.isBlank() }) return@on
            val line = event.lines.firstOrNull { it.plain.trimStart().startsWith(PREFIX) }
            val value = line?.plain?.trim()?.removePrefix(PREFIX)?.trim()
            val match = value?.let(PET_LINE::matchEntire)
            if (value != null && match == null && value != NONE) unparsed(line.plain)
            pet = match?.let {
                val (level, name) = it.destructured
                // The name's span carries the rarity, so the last span with text is the one to read.
                Pet(name, level.toLong(), Role.rarity(line.spans.lastOrNull { span -> span.text.isNotBlank() }?.color) ?: Role.COMMON)
            }
            block = build()
        }
        on<PetNear> { event, _ ->
            val name = PET_LINE.matchEntire(event.name.plain.trim())?.groupValues?.get(2) ?: return@on
            head = name to event.head
            block = build()
        }
    }

    override fun hud(now: Long): HudBlock? {
        if (stale && container.id == INVENTORY && container.slots.size > LAST_SLOT) {
            stale = false
            val next = HashMap<EquipmentPiece, Item>()
            for (piece in ARMOR) container.slots[piece.slot]?.let { next[piece] = it }
            if (next != worn) {
                worn = next
                block = build()
            }
        }
        if (shownLayout != layout.get() || shownPieces != pieces.get() || shownLook != petLook.get()) {
            shownLayout = layout.get()
            shownPieces = pieces.get().toList()
            shownLook = petLook.get()
            block = build()
        }
        return block
    }

    private fun build(): HudBlock? {
        val pet = pet.takeIf { EquipmentPiece.PET in shownPieces }
        val icon = head?.takeIf { pet != null && shownLook == PetLook.ICON && it.first == pet.name }?.second
        val shown = shownPieces.distinct().filter { it != EquipmentPiece.PET || icon != null }.map { if (it == EquipmentPiece.PET) icon else worn[it] }
        val named = pet.takeIf { icon == null }
        if (shown.all { it == null } && named == null) return null
        return block(headed = false) {
            if (shown.any { it != null }) {
                if (shownLayout == EquipmentLayout.ROW) items(shown) else shown.forEach { items(listOf(it)) }
            }
            if (named != null) line { muted("[Lvl ${named.level}] "); rarity(named.name, named.rarity) }
        }
    }

    private companion object {
        /** The player's own inventory, which the client always numbers 0. */
        const val INVENTORY = 0

        val ARMOR = EquipmentPiece.entries.filter { it.slot >= 0 }

        /** The inventory's slots up to the boots. */
        val LAST_SLOT = ARMOR.maxOf { it.slot }

        const val PREFIX = "Pet:"
        const val NONE = "None"
        val PET_LINE = Regex("""\[Lvl (\d+)] (.+)""")
    }
}
