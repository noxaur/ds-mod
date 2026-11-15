package io.github.noxaur.dsmod.core.game

import java.util.Base64

/** A player head's `textures` property is base64 of a JSON that names the player beside the skin. */
object Skulls {
    const val SKINS = "http://textures.minecraft.net/texture/"

    /**
     * The `textures` property for an [Item.skin], naming only the skin's address. The server's own also names the
     * player, their id and the time it was made, which the skin alone leaves behind.
     */
    fun property(skin: String): String = Base64.getEncoder().encodeToString("{\"textures\":{\"SKIN\":{\"url\":\"$SKINS$skin\"}}}".toByteArray())
}
