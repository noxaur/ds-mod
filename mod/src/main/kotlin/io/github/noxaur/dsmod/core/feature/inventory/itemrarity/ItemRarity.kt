package io.github.noxaur.dsmod.core.feature.inventory.itemrarity

import io.github.noxaur.dsmod.core.config.Bind
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Rows
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.container.MarkStyle
import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.container.SlotStyle
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.PetCard
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.ui.Look
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import io.github.noxaur.dsmod.core.feature.SlotMarks

class ItemRarity(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "item-rarity", Area.ANYWHERE, Store.INVENTORY, "Color each slot by its item's rarity.", formerly = listOf("rarityMarks"), slots = ::slots)

    val inventoryStyle = feature.stored("inventoryLook", SlotStyle())

    val hotbarStyle = feature.stored("hotbarLook", SlotStyle())

    val hotbarMatches = feature.stored("hotbarMatches", true)

    init {
        feature.former<String>("rarityPreset")?.let { preset ->
            val mark = PRESETS[preset] ?: feature.former<OldLook>("inventoryStyle")?.mark() ?: MarkStyle.FRAME
            inventoryStyle.value = SlotStyle(mark)
        }
        feature.former<OldLook>("hotbarStyle")?.let { hotbarStyle.value = SlotStyle(it.mark()) }

        feature.rows {
            looks(
                field = inventoryStyle.bind.field,
                idPrefix = "",
                look = ::containerStyle,
                follows = { null },
                write = { inventoryStyle.value = it },
                style = "Pick how the rarity's color fills each slot.",
                opacity = "Set how solid the colors are.",
                sparkles = "Add a few sparkles to each slot.",
            )
        }
    }

    val markCommon = feature.switch("markCommon", "Common Items", "Mark common items too.", default = false)

    val hotbarMarks = feature.switch("hotbarMarks", "Hotbar", "Mark your hotbar as well.", default = true) {
        val match = Bind(hotbarMatches.bind.field, { hotbarMatches.value }) {
            if (!it && hotbarMatches.value) hotbarStyle.value = containerStyle()
            hotbarMatches.value = it
        }
        switch(match, "Match Inventory", "Mark the hotbar in the same look as your inventory.")
        shownWhile({ !hotbarMatches.value }) {
            looks(
                field = hotbarStyle.bind.field,
                idPrefix = "hotbar",
                look = ::hotbarLook,
                follows = { if (hotbarMatches.value) hotbarLook() else null },
                write = {
                    hotbarStyle.value = it
                    hotbarMatches.value = false
                },
                style = "Pick how the color fills each hotbar slot.",
                opacity = "Set how solid the hotbar's colors are.",
                sparkles = "Add a few sparkles to each hotbar slot.",
            )
        }
    }

    fun containerStyle(): SlotStyle = inventoryStyle.value

    fun hotbarLook(): SlotStyle = if (hotbarMatches.value) containerStyle() else hotbarStyle.value

    private var common = markCommon.value

    private val byRarity = Role.RARITIES.associate { it.name to SlotMark(it, rarity = true) }

    private val marks = SlotMarks(feature) { _, item, _ ->
        val rarity = item.rarity
        val mark = if (rarity == null) PetCard.rarity(item)?.let { byRarity[it.name] } else byRarity[rarity]
        if (mark == null && rarity != null) feature.unparsed(rarity)
        mark?.takeIf { it.role != Role.COMMON || common }
    }

    fun slots(now: Long): Map<Int, SlotMark> {
        if (common != markCommon.value) {
            common = !common
            marks.invalidate()
        }
        return marks.marks(now)
    }
}

private fun Rows.looks(
    field: String, idPrefix: String, look: () -> SlotStyle, follows: () -> SlotStyle?, write: (SlotStyle) -> Unit,
    style: String, opacity: String, sparkles: String,
) {
    fun id(part: String) = if (idPrefix.isEmpty()) part else idPrefix + part.replaceFirstChar { it.uppercase() }
    fun <T> part(part: String, get: (SlotStyle) -> T, with: SlotStyle.(T) -> SlotStyle) =
        Bind("$field.$part", { get(look()) }) {
            if (it != get(look())) write(look().with(it))
        }.follows { follows()?.let(get) }
    pick(part("mark", { it.mark }) { copy(mark = it) }.also { if (idPrefix.isEmpty()) it.formerly("rarityPreset") }, "Style", style, MarkStyle.entries, id("style")) {
        Look.Slots(look().copy(mark = it))
    }
    number(part("opacity", { it.opacity.toFloat() }) { copy(opacity = it.roundToInt()) }, "Opacity", opacity, OPACITY_MIN..OPACITY_MAX, OPACITY_STEP, "%", id("opacity"))
    switch(part("sparkles", { it.sparkles }) { copy(sparkles = it) }, "Sparkles", sparkles, id("sparkles"))
}

private const val OPACITY_MIN = 10f
private const val OPACITY_MAX = 100f
private const val OPACITY_STEP = 5f

private val PRESETS = mapOf("GILDED" to MarkStyle.FRAME, "SHOWCASE" to MarkStyle.AURA, "MINIMAL" to MarkStyle.UNDERGLOW)

@Serializable
private class OldLook(val border: String = "GILDED", val fill: String = "GLOW") {
    fun mark() = when {
        fill == "HALO" || border == "ROUNDED" -> MarkStyle.AURA
        border != "NONE" || fill == "GLOW" || fill == "WASH" -> MarkStyle.FRAME
        else -> MarkStyle.UNDERGLOW
    }
}
