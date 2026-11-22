package io.github.noxaur.dsmod.core.feature.inventory

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
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.ui.Look
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * Marks each item's slot in the color the server gives that rarity's item names, unless [markCommon] is off for Common.
 * The look of a mark is the edge's: `Slots` draws [containerStyle] in containers and [hotbarLook] on the hotbar.
 */
class ItemRarity(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "item-rarity", Area.ANYWHERE, Store.INVENTORY, "Mark each item's slot in the color of its rarity.", formerly = listOf("rarityMarks"), slots = ::slots)

    /** The look of the marks in every container, the player's inventory included. */
    val inventoryStyle = feature.stored("inventoryLook", SlotStyle())

    /** The hotbar's own look, which shows once the player unlinks it from [inventoryStyle]. */
    val hotbarStyle = feature.stored("hotbarLook", SlotStyle())

    /** While true the hotbar is drawn in [inventoryStyle], and [hotbarStyle] waits for the player to unlink them. */
    val hotbarMatches = feature.stored("hotbarMatches", true)

    init {
        // Older versions picked a preset, or built a look from a border, a fill and a floor. Each becomes the nearest style.
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
                opacity = "Set how solid the marks are.",
                sparkles = "Add a few glints to each mark, placed differently per slot.",
            )
        }
    }

    val markCommon = feature.switch("markCommon", "Common Items", "Mark common items too.", default = false)

    val hotbarMarks = feature.switch("hotbarMarks", "Hotbar", "Mark your hotbar as well.", default = true) {
        // Unmatching starts the hotbar's own look from what it showed, so nothing jumps.
        val match = Bind(hotbarMatches.bind.field, { hotbarMatches.value }) {
            if (!it && hotbarMatches.value) hotbarStyle.value = containerStyle()
            hotbarMatches.value = it
        }
        switch(match, "Match Inventory", "Mark the hotbar in the same look as your inventory.")
        // A row shows the hotbar's look as it is, and a change makes it the hotbar's own. While the hotbar matches,
        // the row's default is the look on screen, so a reset writes that back and leaves Match Inventory on.
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
            opacity = "Set how solid the hotbar's marks are.",
            sparkles = "Add a few glints to each hotbar mark.",
        )
    }

    /** The style containers show. */
    fun containerStyle(): SlotStyle = inventoryStyle.value

    /** The look the hotbar's marks are drawn in: the inventory's while they match, or the hotbar's own. */
    fun hotbarLook(): SlotStyle = if (hotbarMatches.value) containerStyle() else hotbarStyle.value

    private var marks = emptyMap<Int, SlotMark>()
    private var changed = false
    private var common = markCommon.value

    private val byRarity = Role.RARITIES.associate { it.name to SlotMark(it, rarity = true) }

    init {
        feature.on<ContainerOpened> { _, _ -> changed = true }
        feature.on<SlotChanged> { _, _ -> changed = true }
    }

    fun slots(now: Long): Map<Int, SlotMark> {
        if (common != markCommon.value) {
            common = !common
            changed = true
        }
        if (!changed) return marks
        changed = false
        val next = HashMap<Int, SlotMark>()
        feature.container.slots.forEachIndexed { slot, item ->
            val rarity = item?.rarity ?: return@forEachIndexed
            val mark = byRarity[rarity]
            when {
                mark == null -> feature.unparsed(rarity)
                mark.role != Role.COMMON || common -> next[slot] = mark
            }
        }
        marks = next
        return marks
    }
}

/**
 * The Style, Opacity and Sparkles rows of a slot look, saved under [field]. Each shows [look] as it is, a change goes
 * to [write], and a reset returns to the [follows] look while there is one. A row's id is its part, after [idPrefix].
 */
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

/** The preset an older version saved, by name, as the style nearest it. Custom is missing: its look was built from parts. */
private val PRESETS = mapOf("GILDED" to MarkStyle.FRAME, "SHOWCASE" to MarkStyle.AURA, "MINIMAL" to MarkStyle.UNDERGLOW)

/** A look as an older version saved it, from parts, read only to carry it over. Its defaults were the Gilded preset's. */
@Serializable
private class OldLook(val border: String = "GILDED", val fill: String = "GLOW") {
    /** The style nearest it: a round look is an Aura, one with a border or a fill across the slot a Frame, and the rest an Underglow. */
    fun mark() = when {
        fill == "HALO" || border == "ROUNDED" -> MarkStyle.AURA
        border != "NONE" || fill == "GLOW" || fill == "WASH" -> MarkStyle.FRAME
        else -> MarkStyle.UNDERGLOW
    }
}
