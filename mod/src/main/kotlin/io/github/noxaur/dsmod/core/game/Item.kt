package io.github.noxaur.dsmod.core.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * An item stack as the server sent it. [name] and [lore] are the custom name and lore, which the server uses
 * for everything the player reads, and [data] is the whole `custom_data`, where the server keeps what it knows.
 * The rest is how the item looks, so the mod can draw it as the server does: [skin] is a head's texture, the last
 * part of its `textures.minecraft.net` address, [dye] is dyed leather's color, [glint] overrides the enchantment
 * shine, and [style] is the tooltip's frame, such as `dragsim:legendary`.
 */
@Serializable
data class Item(
    val id: String,
    val count: Int,
    val name: ServerText? = null,
    val lore: List<ServerText> = emptyList(),
    val data: JsonObject = JsonObject(emptyMap()),
    val skin: String? = null,
    @Serializable(with = HexColor::class) val dye: Int? = null,
    val glint: Boolean? = null,
    val style: String? = null,
) {
    /** The rarity in `item_data`, such as `LEGENDARY`, or null for an item with none. */
    val rarity: String? get() = ((data["item_data"] as? JsonObject)?.get("rarity") as? JsonPrimitive)?.contentOrNull
}
