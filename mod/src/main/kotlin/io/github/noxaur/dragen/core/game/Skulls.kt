package io.github.noxaur.dragen.core.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

// @keep A player head's `textures` property is base64 of a JSON that names the player beside the skin
object Skulls {
    const val SKINS = "http://textures.minecraft.net/texture/"

    fun property(skin: String): String = Base64.getEncoder().encodeToString("{\"textures\":{\"SKIN\":{\"url\":\"$SKINS$skin\"}}}".toByteArray())

    /** @keep A property from another client, such as the backend's, is checked before the game is given it, so drawing a
     * skin fetches nothing from anywhere but Mojang's texture host */
    fun onMojang(property: String): Boolean {
        val urls = runCatching {
            Json.parseToJsonElement(String(Base64.getDecoder().decode(property))).jsonObject["textures"]?.jsonObject?.values
                ?.map { it.jsonObject["url"]?.jsonPrimitive?.takeIf { url -> url.isString }?.content }
        }.getOrNull() ?: return false
        return urls.isNotEmpty() && urls.all { url ->
            url != null && runCatching { java.net.URI(url) }.getOrNull()?.let { it.scheme in setOf("http", "https") && it.host == "textures.minecraft.net" } == true
        }
    }
}
