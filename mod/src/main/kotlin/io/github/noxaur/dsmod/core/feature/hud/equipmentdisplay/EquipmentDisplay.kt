package io.github.noxaur.dsmod.core.feature.hud.equipmentdisplay

import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.hud.HudDock
import io.github.noxaur.dsmod.core.hud.HudLook
import io.github.noxaur.dsmod.core.feature.ContainerReader
import io.github.noxaur.dsmod.core.feature.hud.CurrentPet

enum class EquipmentPiece(private val label: String, val slot: Int) {
    HELMET("Helmet", 5),
    CHESTPLATE("Chestplate", 6),
    LEGGINGS("Leggings", 7),
    BOOTS("Boots", 8),
    PET("Pet", -1),
    ;

    override fun toString() = label
}

enum class EquipmentLayout(private val label: String) {
    ROW("Row"),
    COLUMN("Column"),
    ;

    override fun toString() = label
}

enum class EquipmentLook(private val label: String, val hud: HudLook) {
    PLATE("Plate", HudLook.PLATE),
    CLEAR("Clear", HudLook.CLEAR),
    HOTBAR("Hotbar", HudLook.HOTBAR),
    ;

    override fun toString() = label
}

enum class PetPosition(private val label: String) {
    WITH_ARMOR("With Armor"),
    ABOVE("Above"),
    BELOW("Below"),
    ;

    override fun toString() = label
}

enum class PetLook(private val label: String) {
    ICON("Icon"),
    NAME("Name"),
    ;

    override fun toString() = label
}

/** @keep The pet comes from the sidebar's line `Pet: [Lvl 100] Ender Dragon`, and dragsim.co floats each pet beside its owner, so the head of the last pet seen by that name is its icon */
class EquipmentDisplay(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "equipment-display", Area.ANYWHERE, Store.GENERAL, "Show your armor and pet on the HUD.", hud = ::hud)
    private val inventory = ContainerReader(feature, ARMOR.minOf { it.slot }..ARMOR.maxOf { it.slot })

    val pieces = feature.order("equipmentPieces", "Pieces", "Pick the pieces to show, and drag them into order.", EquipmentPiece.entries)
    val layout = feature.choice("equipmentLayout", "Layout", "Line the pieces up in a row or a column.", EquipmentLayout.ROW)
    val look = feature.choice("equipmentLook", "Look", "Show the pieces on the plate, on nothing, or in hotbar slots.", EquipmentLook.PLATE)
    val placement = feature.placement("equipmentPlacement", "Keep the pieces where you put them, or beside your hotbar.")
    val petLook = feature.choice("petLook", "Pet Look", "Show your pet as its head, or as its name and level.", PetLook.ICON)
    val petPosition = feature.choice("petPosition", "Pet Position", "Put your pet among the armor, or on a row above or below it.", PetPosition.WITH_ARMOR)

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
