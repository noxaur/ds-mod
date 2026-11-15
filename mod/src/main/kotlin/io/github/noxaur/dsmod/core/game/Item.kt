package io.github.noxaur.dsmod.core.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * An item stack as the server sent it. [name] and [lore] are the custom name and lore, which the server uses
 * for everything the player reads, and [data] is the whole `custom_data`, where the server keeps what it knows.
 * How the game draws it is [glint], [model], [skull] and [color], which a capture keeps only when they are set.
 */
@Serializable
data class Item(
    val id: String,
    val count: Int,
    val name: ServerText? = null,
    val lore: List<ServerText> = emptyList(),
    val data: JsonObject = JsonObject(emptyMap()),
    /** Whether the game draws the item with the enchantment glint. */
    val glint: Boolean = false,
    /** The `item_model` component, when it names a model other than [id]'s own. */
    val model: String? = null,
    /** A player head's `textures` property cut to `{"textures":{...}}`, because the profile also names its player. */
    val skull: String? = null,
    /** A dyed item's color, as `0xRRGGBB`. */
    val color: Int? = null,
) {
    /** The rarity in `item_data`, such as `LEGENDARY`, or null for an item with none. */
    val rarity: String? get() = ((data["item_data"] as? JsonObject)?.get("rarity") as? JsonPrimitive)?.contentOrNull
}
