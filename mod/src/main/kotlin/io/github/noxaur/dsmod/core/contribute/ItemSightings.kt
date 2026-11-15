package io.github.noxaur.dsmod.core.contribute

import io.github.noxaur.dsmod.core.backend.ItemSighting
import io.github.noxaur.dsmod.core.capture.Redactor
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.Skulls
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Collects how the player's own items look, for the player who agreed to send it. A sighting is one look of one item
 * `entry`, so two items that look the same go once. [own] gives the names the player surely sees, their own and the tab
 * list's, which the [Redactor] removes from every name and lore line. Call it on the client thread.
 */
class ItemSightings(private val own: () -> Set<String>) :
    Batch<ItemSighting>({ listOf(it.entry, it.glint, it.skinId, it.skullTexture, it.lore) }) {

    /** Collects what [item] looks like, unless it is not an item of the server's, or the backend would refuse it. */
    fun see(item: Item) {
        if (full) return
        val redacted = (Redactor(own()).redact(SlotChanged(0, item)) as SlotChanged).item ?: return
        // The redactor drops a skin, as a capture must, but a head's texture is what a sighting is for. The skin is only
        // its address, with no name or id beside it, and the backend draws it only once a second player saw it.
        add(sighting(redacted.copy(skin = item.skin)) ?: return)
    }

    private fun sighting(item: Item): ItemSighting? {
        val entry = item.string("item_data", "item_id") ?: return null
        val skinId = item.string("extra", "item_skin", "skinId")
        val sighting = ItemSighting(
            entry,
            item.id,
            item.model,
            skinId,
            item.name?.let(::withoutUuids) ?: ServerText(emptyList()),
            item.lore.take(MAX_LORE).map(::withoutUuids),
            item.foil,
            item.skin?.let(Skulls::property),
            item.dye?.and(RGB),
            allowed(item.data),
        )
        val fits = listOfNotNull(sighting.entry, sighting.material, sighting.itemModel, sighting.skinId).all { it.length <= MAX_ID } &&
            (sighting.skullTexture?.length ?: 0) <= MAX_TEXTURE
        return sighting.takeIf { fits }
    }

    private fun Item.string(vararg path: String): String? {
        var at: JsonElement? = data
        for (key in path) at = (at as? JsonObject)?.get(key)
        return (at as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    }

    private fun withoutUuids(text: ServerText) = ServerText(text.spans.map { ServerSpan(UUID.replace(it.text, ""), it.color, it.bold, it.italic, it.underlined, it.strikethrough, it.obfuscated) })

    private fun withoutUuids(json: JsonElement): JsonElement = when (json) {
        is JsonObject -> JsonObject(json.mapValues { (_, value) -> withoutUuids(value) })
        is JsonArray -> JsonArray(json.map(::withoutUuids))
        is JsonPrimitive -> if (json.isString) JsonPrimitive(UUID.replace(json.content, "")) else json
    }

    /**
     * What of `custom_data` goes: the keys that say how an item is built, and inside `item_data` and `extra` only the
     * keys listed in [ITEM_DATA] and [EXTRA]. The ids of the copy and anything else the server keeps there, now or
     * added later, stay on the player's machine.
     */
    private fun allowed(data: JsonObject): JsonObject {
        val kept = HashMap<String, JsonElement>()
        (data["item_data"] as? JsonObject)?.let { kept["item_data"] = JsonObject(it.filterKeys { key -> key in ITEM_DATA }) }
        (data["extra"] as? JsonObject)?.let { kept["extra"] = extra(it) }
        for (key in ALLOWED) data[key]?.let { kept[key] = it }
        return withoutUuids(JsonObject(kept)) as JsonObject
    }

    /** `extra` keeps the building blocks the renderer reads. A skin is only its `skinId`. */
    private fun extra(extra: JsonObject): JsonObject {
        val kept = extra.filterKeys { it in EXTRA }.toMutableMap()
        (extra["item_skin"] as? JsonObject)?.get("skinId")?.let { kept["item_skin"] = JsonObject(mapOf("skinId" to it)) } ?: kept.remove("item_skin")
        return JsonObject(kept)
    }

    private companion object {
        const val MAX_LORE = 80
        const val MAX_ID = 128
        const val MAX_TEXTURE = 4096
        /** A dyed color without the alpha the game may keep above it. */
        const val RGB = (1 shl 24) - 1
        val ITEM_DATA = setOf("npc_sell_price", "reforge_stats", "color", "item_id", "reforge", "item_type", "item_name", "skull", "rarity")
        val EXTRA = setOf("item_dye", "item_skin", "dungeon_upgrade", "wither_scrolls", "coating", "attunement", "drill", "enrichment")
        val ALLOWED = listOf("item_stats", "item_modifiers", "abilities")
        val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}
