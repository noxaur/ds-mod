package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.game.Item
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
 * active pet's item in the Pets menu, whose tooltip names its held item, or else the head of the last pet seen whose
 * name is the sidebar's: dragsim.co floats each pet beside its owner. Until one of those is seen, or with [petLook]
 * on Name, the pet is a line under the icons, as the sidebar writes it, with its held item once the menu showed it.
 */
class EquipmentDisplay(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "equipment-display", Area.ANYWHERE, Store.GENERAL, "Show your armor and pet on the HUD.", hud = ::hud)
    private val inventory = ContainerReader(feature, ARMOR.minOf { it.slot }..ARMOR.maxOf { it.slot })

    val pieces = feature.order("equipmentPieces", "Pieces", "Pick the pieces to show, and drag them into order.", EquipmentPiece.entries)
    val layout = feature.choice("equipmentLayout", "Layout", "Line the pieces up in a row or a column.", EquipmentLayout.ROW)
    val look = feature.choice("equipmentLook", "Look", "Show the pieces on the plate, on nothing, or in hotbar slots.", EquipmentLook.PLATE)
    val placement = feature.placement("equipmentPlacement", "Keep the pieces where you put them, or beside your hotbar.")
    val petLook = feature.choice("petLook", "Pet Look", "Show your pet as its head, or as its name and level.", PetLook.ICON)
    val petPosition = feature.choice("petPosition", "Pet Position", "Put your pet among the armor, or on a row above or below it.", PetPosition.WITH_ARMOR)

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
    private val pets = CurrentPet(feature, { block = build() }, { _, card, _ -> if (card != null) block = build() }) { block = build() }

    private var shown = settings()
    private var block: HudBlock? = null

    fun hud(now: Long): HudBlock? {
        if (inventory.changed()) {
            val next = HashMap<EquipmentPiece, Item>()
            for (piece in ARMOR) feature.container.slots[piece.slot]?.let { next[piece] = it }
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
        val pet = pets.pet.takeIf { EquipmentPiece.PET in shown.pieces }
        val card = pets.card
        val icon = pet?.takeIf { shown.petLook == PetLook.ICON }?.let { out -> card?.takeIf { it.pet.name == out.name }?.icon ?: pets.head(out.name) }
        val inline = shown.petPosition == PetPosition.WITH_ARMOR
        val armor = shown.pieces.distinct()
            .filter { if (it == EquipmentPiece.PET) inline && icon != null else true }
            .map { if (it == EquipmentPiece.PET) icon else worn[it] }
        val apart = icon.takeIf { !inline }
        val named = pet.takeIf { icon == null }
        if (armor.all { it == null } && apart == null && named == null) return null
        return feature.block(headed = false, look = shown.look.hud, dock = shown.placement) {
            val armorRows = {
                if (armor.any { it != null }) {
                    if (shown.layout == EquipmentLayout.ROW) items(armor) else armor.forEach { items(listOf(it)) }
                }
            }
            val petRows = {
                if (apart != null) items(listOf(apart))
                if (named != null) {
                    val holds = card?.held?.name?.takeIf { card.pet.name == named.name }
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
    }
}
