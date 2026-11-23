package io.github.noxaur.dsmod.core.contribute

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class ContractTest {
    private val paths = Json.parseToJsonElement(Path.of("../api/openapi.json").readText()).jsonObject.getValue("paths").jsonObject

    private fun payload(path: String) = paths.getValue(path).jsonObject.getValue("post").jsonObject
        .at("requestBody", "content", "application/json", "schema", "properties")

    private fun JsonElement.at(vararg keys: String): JsonElement = keys.fold(this) { node, key -> node.jsonObject.getValue(key) }

    private fun JsonElement.max(key: String) = jsonObject.getValue(key).jsonPrimitive.int

    private fun within(mod: Int, contract: Int, what: String) = assertTrue(mod <= contract, "$what: the mod keeps $mod, the contract takes $contract")

    @Test
    fun `an item sighting the mod keeps fits the contract`() {
        val items = payload("/v1/contrib/items").at("items")
        within(Batch.MAX_BATCH, items.max("maxItems"), "items")
        val fields = items.at("items", "properties")
        for (id in listOf("entry", "material", "itemModel", "skinId")) within(ItemSightings.MAX_ID, fields.at(id).max("maxLength"), id)
        within(ItemSightings.MAX_TEXTURE, fields.at("skullTexture").max("maxLength"), "skullTexture")
        within(ItemSightings.MAX_LORE, fields.at("lore").max("maxItems"), "lore")
        for (line in listOf(fields.at("name"), fields.at("lore", "items"))) {
            within(ItemSightings.MAX_LINE_SPANS, line.max("maxItems"), "spans")
            within(ItemSightings.MAX_SPAN_TEXT, line.at("items", "properties", "text").max("maxLength"), "span text")
        }
    }

    @Test
    fun `an unknown the mod keeps fits the contract`() {
        val sightings = payload("/v1/contrib/unknowns").at("sightings")
        within(Batch.MAX_BATCH, sightings.max("maxItems"), "sightings")
        val fields = sightings.at("items", "properties")
        within(Unknowns.MAX_KIND, fields.at("kind").max("maxLength"), "kind")
        within(Unknowns.MAX_SIGNATURE, fields.at("signature").max("maxLength"), "signature")
        within(Unknowns.MAX_SAMPLE, fields.at("sample").max("maxLength"), "sample")
    }
}
