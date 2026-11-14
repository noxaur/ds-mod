package io.github.noxaur.dsmod.core.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * An item stack as the server sent it. [name] and [lore] are the custom name and lore, which the server uses
 * for everything the player reads, and [data] is the whole `custom_data`, where the server keeps what it knows.
 */
@Serializable
data class Item(
    val id: String,
    val count: Int,
    val name: ServerText? = null,
    val lore: List<ServerText> = emptyList(),
    val data: JsonObject = JsonObject(emptyMap()),
) {
    /** The rarity in `item_data`, such as `LEGENDARY`, or null for an item with none. */
    val rarity: String? get() = ((data["item_data"] as? JsonObject)?.get("rarity") as? JsonPrimitive)?.contentOrNull
}
