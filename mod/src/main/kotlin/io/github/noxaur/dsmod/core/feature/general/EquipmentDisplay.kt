package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.game.ContainerState.Companion.INVENTORY
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.LeftServer
import io.github.noxaur.dsmod.core.game.Pet
import io.github.noxaur.dsmod.core.game.PetCard
import io.github.noxaur.dsmod.core.game.PetNear
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.hud.HudDock
import io.github.noxaur.dsmod.core.hud.HudLook

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

/** What the pieces sit on: the HUD's plate, nothing, or slots like the hotbar's. */
enum class EquipmentLook(private val label: String, val hud: HudLook) {
    PLATE("Plate", HudLook.PLATE),
    CLEAR("Clear", HudLook.CLEAR),
    HOTBAR("Hotbar", HudLook.HOTBAR),
    ;

    override fun toString() = label
}

/** Where the pet sits: among the armor in the order of the pieces, or on a row of its own above or below them. */
enum class PetPosition(private val label: String) {
    WITH_ARMOR("With Armor"),
    ABOVE("Above"),
    BELOW("Below"),
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
 * menu does not hold the armor. The pet comes from the sidebar's line `Pet: [Lvl 100] Ender Dragon`. Its icon is the
 * active pet's item in the Pets menu, whose tooltip names its held item, or else the head of the nearest pet whose
 * name is the sidebar's: dragsim.co floats each pet beside its owner. Until one of those is seen, or with [petLook]
 * on Name, the pet is a line under the icons, as the sidebar writes it, with its held item once the menu showed it.
 */
class EquipmentDisplay(config: DsModConfig) : InventoryReader(config, "equipment-display", "Show your armor and pet on the HUD.") {
    val pieces = order("equipmentPieces", "Pieces", "Pick the pieces to show, and drag them into order.", EquipmentPiece.entries)
    val layout = choice("equipmentLayout", "Layout", "Line the pieces up in a row or a column.", EquipmentLayout.ROW)
    val look = choice("equipmentLook", "Look", "Show the pieces on the plate, on nothing, or in hotbar slots.", EquipmentLook.PLATE)
    override val placement = choice("equipmentPlacement", "Placement", "Keep the pieces where you put them, or beside your hotbar.", HudDock.FREE)
    val petLook = choice("petLook", "Pet Look", "Show your pet as its head, or as its name and level.", PetLook.ICON)
    val petPosition = choice("petPosition", "Pet Position", "Put your pet among the armor, or on a row above or below it.", PetPosition.WITH_ARMOR)

    /** The settings the block was built with, so a refresh rebuilds it only when one changed. */
    private data class Shown(
        val layout: EquipmentLayout,
        val pieces: List<EquipmentPiece>,
        val petLook: PetLook,
        val petPosition: PetPosition,
        val look: EquipmentLook,
        val placement: HudDock,
    )

    private var worn = emptyMap<EquipmentPiece, Item>()
    private var pet: Pet? = null

    // Each by the pet's name without its level, which goes up in play: the active pet's item in the Pets menu, the
    // item it holds, and the head of each pet seen near the player. A pet's head is the same whoever owns it, and
    // another player's pet can float nearer than the player's own, so the sidebar's pet picks its head from all of them.
    private var menuPet: Pair<String, Item>? = null
    private var held: String? = null
    private val heads = HashMap<String, Item>()

    private var shown = settings()
    private var block: HudBlock? = null

    init {
        on<SlotChanged> { event, _ ->
            if (container.id == INVENTORY && ARMOR.any { it.slot == event.slot }) stale = true
            if (container.title?.plain != PetCard.MENU) return@on
            val card = event.item?.let(PetCard::of) ?: return@on
            menuPet = card.pet.name to card.icon
            held = card.held?.name
            block = build()
        }
        // The next session may bring another pet, and the sidebar names it only when it arrives. A head stays its pet's.
        on<LeftServer> { _, _ ->
            pet = null
            menuPet = null
            held = null
            block = build()
        }
        on<Sidebar> { event, _ ->
            // Between worlds the sidebar is empty until the new one arrives, and the pet is still out.
            if (event.lines.all { it.plain.isBlank() }) return@on
            val line = event.lines.firstOrNull { it.plain.trimStart().startsWith(PREFIX) }
            val value = line?.plain?.trim()?.removePrefix(PREFIX)?.trim()
            pet = value?.let { Pet.of(line, it) }
            if (value != null && pet == null && value != NONE) unparsed(line.plain)
            block = build()
        }
        on<PetNear> { event, _ ->
            val name = Pet.nameOf(event.name.plain) ?: return@on
            heads[name] = event.head
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
        // Compared field by field, so a refresh with nothing new allocates nothing.
        if (shown.layout != layout.value || shown.pieces != pieces.value || shown.petLook != petLook.value ||
            shown.petPosition != petPosition.value || shown.look != look.value || shown.placement != placement.value
        ) {
            shown = settings()
            block = build()
        }
        return block
    }

    private fun settings() = Shown(layout.value, pieces.value, petLook.value, petPosition.value, look.value, placement.value)

    private fun build(): HudBlock? {
        val pet = pet.takeIf { EquipmentPiece.PET in shown.pieces }
        val icon = pet?.takeIf { shown.petLook == PetLook.ICON }?.let { menuPet?.takeIf { (name) -> name == it.name }?.second ?: heads[it.name] }
        val inline = shown.petPosition == PetPosition.WITH_ARMOR
        val armor = shown.pieces.distinct()
            .filter { if (it == EquipmentPiece.PET) inline && icon != null else true }
            .map { if (it == EquipmentPiece.PET) icon else worn[it] }
        val apart = icon.takeIf { !inline }
        val named = pet.takeIf { icon == null }
        if (armor.all { it == null } && apart == null && named == null) return null
        return block(headed = false, look = shown.look.hud, dock = shown.placement) {
            val armorRows = {
                if (armor.any { it != null }) {
                    if (shown.layout == EquipmentLayout.ROW) items(armor) else armor.forEach { items(listOf(it)) }
                }
            }
            val petRows = {
                if (apart != null) items(listOf(apart))
                if (named != null) {
                    val holds = held?.takeIf { menuPet?.first == named.name }
                    line {
                        muted("[Lvl ${named.level}] ")
                        rarity(named.name, named.rarity)
                        if (holds != null) muted(" · $holds")
                    }
                }
            }
            if (shown.petPosition == PetPosition.ABOVE) {
                petRows()
                armorRows()
            } else {
                armorRows()
                petRows()
            }
        }
    }

    private companion object {
        val ARMOR = EquipmentPiece.entries.filter { it.slot >= 0 }

        /** The inventory's slots up to the boots. */
        val LAST_SLOT = ARMOR.maxOf { it.slot }

        const val PREFIX = "Pet:"
        const val NONE = "None"
    }
}
