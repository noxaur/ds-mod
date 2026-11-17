package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.config.Bind
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Rows
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.container.Border
import io.github.noxaur.dsmod.core.container.Fill
import io.github.noxaur.dsmod.core.container.Floor
import io.github.noxaur.dsmod.core.container.RarityPreset
import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.container.SlotStyle
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.ui.Look

/**
 * Marks each item's slot in the color the server gives that rarity's item names, unless [markCommon] is off for Common.
 * The look of a mark is the edge's: `Slots` draws [containerStyle] in containers and [hotbarLook] on the hotbar.
 */
class ItemRarity(config: DsModConfig) :
    Feature(config, "item-rarity", Area.ANYWHERE, Store.INVENTORY, "Mark each item's slot in the color of its rarity.", formerly = listOf("rarityMarks")) {
    /** The player's own style for every container, their inventory included, shown while [preset] is Custom. */
    val inventoryStyle = stored("inventoryStyle", SlotStyle())
    val hotbarStyle = stored("hotbarStyle", SlotStyle())

    /** While true the hotbar is drawn in [inventoryStyle], and [hotbarStyle] waits for the player to unlink them. */
    val hotbarMatches = stored("hotbarMatches", true)

    val preset = pick("rarityPreset", "Style", "Pick a look for the marks, or Custom for your own.", RarityPreset.GILDED) { Look.Slots(it.style ?: inventoryStyle.value) }

    init {
        rows {
            fold("customLook", "Custom Look", "Build your own look from its parts, which switches Style to Custom.") {
                // A part shows the look on screen. Writing a different one makes that look the player's own.
                // While a preset is active the part's default is that preset, so a reset leaves it in place.
                slotParts(
                    field = inventoryStyle.bind.field,
                    idPrefix = "",
                    look = ::containerStyle,
                    preset = { preset.value.style },
                    write = {
                        inventoryStyle.value = it
                        preset.value = RarityPreset.CUSTOM
                    },
                    border = "Pick the ring around the slot.",
                    fill = "Pick the color inside the slot, behind the item.",
                    floor = "Pick what the item stands on.",
                    gem = "Put a small gem over the item's top-left corner.",
                )
            }
        }
    }

    val markCommon = switch("markCommon", "Common Items", "Mark common items too.", default = false)

    val hotbarMarks = switch("hotbarMarks", "Hotbar", "Mark your hotbar as well.", default = true) {
        // Unmatching starts the hotbar's own look from what it showed, so nothing jumps.
        val match = Bind(hotbarMatches.bind.field, { hotbarMatches.value }) {
            if (!it && hotbarMatches.value) hotbarStyle.value = containerStyle()
            hotbarMatches.value = it
        }
        switch(match, "Match Inventory", "Mark the hotbar in the same look as your inventory.")
        // A part shows the hotbar's look as it is, and a change makes it the hotbar's own, as Custom Look does
        // for Style. While the hotbar matches, the part's default is the look on screen, so a reset
        // writes that part back and leaves Match Inventory on.
        slotParts(
            field = hotbarStyle.bind.field,
            idPrefix = "hotbar",
            look = ::hotbarLook,
            preset = { if (hotbarMatches.value) hotbarLook() else null },
            write = {
                hotbarStyle.value = it
                hotbarMatches.value = false
            },
            border = "Pick the ring around each hotbar slot.",
            fill = "Pick the color inside each hotbar slot.",
            floor = "Pick what each hotbar item stands on.",
            gem = "Put a small gem over each hotbar item's corner.",
        )
    }

    /** The style containers show: the preset's, or the player's own under Custom. */
    fun containerStyle(): SlotStyle = preset.value.style ?: inventoryStyle.value

    /** The look the hotbar's marks are drawn in: the inventory's while they match, or the hotbar's own. */
    fun hotbarLook(): SlotStyle = if (hotbarMatches.value) containerStyle() else hotbarStyle.value

    private var marks = emptyMap<Int, SlotMark>()
    private var changed = false
    private var common = markCommon.value

    private val byRarity = Role.RARITIES.associate { it.name to SlotMark(it, rarity = true) }

    init {
        on<ContainerOpened> { _, _ -> changed = true }
        on<SlotChanged> { _, _ -> changed = true }
    }

    override fun slots(now: Long): Map<Int, SlotMark> {
        if (common != markCommon.value) {
            common = !common
            changed = true
        }
        if (!changed) return marks
        changed = false
        val next = HashMap<Int, SlotMark>()
        container.slots.forEachIndexed { slot, item ->
            val rarity = item?.rarity ?: return@forEachIndexed
            val mark = byRarity[rarity]
            when {
                mark == null -> unparsed(rarity)
                mark.role != Role.COMMON || common -> next[slot] = mark
            }
        }
        marks = next
        return marks
    }
}

/**
 * The Border, Fill, Floor and Gem rows of a slot look, saved under [field]. Each shows [look] as it is, a change goes
 * to [write], and a reset leaves the [preset] look in place while there is one. A row's id is its part, after [idPrefix].
 */
private fun Rows.slotParts(
    field: String, idPrefix: String, look: () -> SlotStyle, preset: () -> SlotStyle?, write: (SlotStyle) -> Unit,
    border: String, fill: String, floor: String, gem: String,
) {
    fun id(part: String) = if (idPrefix.isEmpty()) part else idPrefix + part.replaceFirstChar { it.uppercase() }
    fun <T> part(part: String, get: (SlotStyle) -> T, with: SlotStyle.(T) -> SlotStyle) =
        Bind("$field.$part", { get(look()) }) {
            if (it != get(look())) write(look().with(it))
        }.follows { preset()?.let(get) }
    fun <T> choose(part: String, name: String, description: String, values: List<T>, label: (T) -> String, get: (SlotStyle) -> T, with: SlotStyle.(T) -> SlotStyle) =
        pick(part(part, get, with), name, description, values, id(part), label = label) { Look.Slots(look().with(it), 1) }
    choose("border", "Border", border, Border.entries, { it.label }, { it.border }) { copy(border = it) }
    choose("fill", "Fill", fill, Fill.entries, { it.label }, { it.fill }) { copy(fill = it) }
    choose("floor", "Floor", floor, Floor.entries, { it.label }, { it.floor }) { copy(floor = it) }
    switch(part("gem", { it.gem }) { copy(gem = it) }, "Gem", gem, id("gem"))
}
