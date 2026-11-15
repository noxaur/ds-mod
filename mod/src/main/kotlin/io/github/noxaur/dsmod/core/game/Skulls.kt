package io.github.noxaur.dsmod.core.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Base64

/** A player head's `textures` property is base64 of a JSON that names the player beside the skin. */
object Skulls {
    /**
     * Whether every skin [property] names is on Mojang's texture host, so drawing it fetches nothing from anywhere
     * else. A property from another client, such as the backend's, is checked before the game is given it.
     */
    fun onMojang(property: String): Boolean {
        val textures = runCatching {
            Json.parseToJsonElement(String(Base64.getDecoder().decode(property))).jsonObject["textures"]?.jsonObject
        }.getOrNull() ?: return false
        val urls = textures.values.map { ((it as? JsonObject)?.get("url") as? JsonPrimitive)?.takeIf { url -> url.isString }?.content }
        return urls.isNotEmpty() && urls.all { url ->
            url != null && runCatching { java.net.URI(url) }.getOrNull()?.let { it.scheme in setOf("http", "https") && it.host == "textures.minecraft.net" } == true
        }
    }

    /**
     * [property] with only the skins' urls kept, so it no longer holds the player's name, id or the time it was made.
     * Null for a value that is not a texture property.
     */
    fun withoutPlayer(property: String): String? {
        val textures = runCatching {
            Json.parseToJsonElement(String(Base64.getDecoder().decode(property))).jsonObject["textures"]?.jsonObject
        }.getOrNull() ?: return null
        val urls = textures.mapNotNull { (kind, texture) ->
            val url = (texture as? JsonObject)?.get("url") as? JsonPrimitive
            url?.takeIf { it.isString }?.let { kind to it.jsonPrimitive.content }
        }
        if (urls.isEmpty()) return null
        val cut = buildJsonObject {
            put(
                "textures",
                buildJsonObject { for ((kind, url) in urls) put(kind, buildJsonObject { put("url", url) }) },
            )
        }
        return Base64.getEncoder().encodeToString(cut.toString().toByteArray())
    }
}
