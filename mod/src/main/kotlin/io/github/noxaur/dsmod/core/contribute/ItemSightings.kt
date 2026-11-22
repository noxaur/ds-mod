package io.github.noxaur.dsmod.core.contribute

import io.github.noxaur.dsmod.core.backend.ItemSighting
import io.github.noxaur.dsmod.core.capture.Redactor
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.Skulls
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ItemSightings(private val own: () -> Set<String>) {
    val batch = Batch<ItemSighting> { listOf(it.entry, it.glint, it.skinId, it.skullTexture, it.lore) }

    fun see(item: Item) {
        if (batch.full) return
        val redacted = (Redactor(own()).redact(SlotChanged(0, item)) as SlotChanged).item ?: return
        /** @keep The redactor drops a skin, as a capture must, but a head's texture is what a sighting is for. The skin is
         * only its address, with no name or id beside it, and the backend draws it only once a second player saw it. */
        batch.add(sighting(redacted.copy(skin = item.skin)) ?: return)
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
            allowed(item),
        )
        val fits = listOfNotNull(sighting.entry, sighting.material, sighting.itemModel, sighting.skinId).all { it.length <= MAX_ID } &&
            (sighting.skullTexture?.length ?: 0) <= MAX_TEXTURE
        return sighting.takeIf { fits }
    }

    private fun withoutUuids(text: ServerText) = ServerText(text.spans.map { it.copy(text = UUID.replace(it.text, "")) })

    private fun withoutUuids(json: JsonElement): JsonElement = when (json) {
        is JsonObject -> JsonObject(json.mapValues { (_, value) -> withoutUuids(value) })
        is JsonArray -> JsonArray(json.map(::withoutUuids))
        is JsonPrimitive -> if (json.isString) JsonPrimitive(UUID.replace(json.content, "")) else json
    }

    /** @keep The ids of the copy and anything else the server keeps in custom_data, now or added later, stay on the player's
     * machine. */
    private fun allowed(item: Item): JsonObject {
        val kept = HashMap<String, JsonElement>()
        item.objectAt("item_data")?.let { kept["item_data"] = JsonObject(it.filterKeys { key -> key in ITEM_DATA }) }
        item.objectAt("extra")?.let { kept["extra"] = extra(it, item.objectAt("extra", "item_skin")) }
        for (key in ALLOWED) item.data[key]?.let { kept[key] = it }
        return withoutUuids(JsonObject(kept)) as JsonObject
    }

    private fun extra(extra: JsonObject, skin: JsonObject?): JsonObject {
        val kept = extra.filterKeys { it in EXTRA }.toMutableMap()
        skin?.get("skinId")?.let { kept["item_skin"] = JsonObject(mapOf("skinId" to it)) } ?: kept.remove("item_skin")
        return JsonObject(kept)
    }

    private companion object {
        const val MAX_LORE = 80
        const val MAX_ID = 128
        const val MAX_TEXTURE = 4096
        const val RGB = (1 shl 24) - 1
        val ITEM_DATA = setOf("npc_sell_price", "reforge_stats", "color", "item_id", "reforge", "item_type", "item_name", "skull", "rarity")
        val EXTRA = setOf("item_dye", "item_skin", "dungeon_upgrade", "wither_scrolls", "coating", "attunement", "drill", "enrichment")
        val ALLOWED = listOf("item_stats", "item_modifiers", "abilities")
        val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}
