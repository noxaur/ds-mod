package io.github.noxaur.dsmod.core.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.util.Base64

/** A player head's `textures` property is base64 of a JSON that names the player beside the skin. */
object Skulls {
    const val SKINS = "http://textures.minecraft.net/texture/"

    /**
     * The `textures` property for an [Item.skin], naming only the skin's address. The server's own also names the
     * player, their id and the time it was made, which the skin alone leaves behind.
     */
    fun property(skin: String): String = Base64.getEncoder().encodeToString("{\"textures\":{\"SKIN\":{\"url\":\"$SKINS$skin\"}}}".toByteArray())

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
}
