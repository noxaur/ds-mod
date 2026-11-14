package io.github.noxaur.dsmod.debug.capture

import com.google.gson.JsonArray as GsonArray
import com.google.gson.JsonElement as GsonElement
import com.google.gson.JsonObject as GsonObject
import com.google.gson.JsonPrimitive as GsonPrimitive
import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.world.item.ItemStack

/** The plain text, plus the full component so colours and hover text survive. */
fun Component.toCaptureJson(): JsonObject = buildJsonObject {
    put("text", string)
    put("component", encode(ComponentSerialization.CODEC, this@toCaptureJson))
}

/** The item with all its data components, as the server sent it. */
fun ItemStack.toCaptureJson(): JsonElement = encode(ItemStack.CODEC, this)

private fun <T : Any> encode(codec: Codec<T>, value: T): JsonElement {
    val registries = Minecraft.getInstance().connection?.registryAccess()
    val ops = registries?.createSerializationContext(JsonOps.INSTANCE) ?: JsonOps.INSTANCE
    return codec.encodeStart(ops, value).result().map { it.toKotlinx() }.orElse(JsonNull)
}

private fun GsonElement.toKotlinx(): JsonElement = when (this) {
    is GsonObject -> JsonObject(entrySet().associate { it.key to it.value.toKotlinx() })
    is GsonArray -> JsonArray(map { it.toKotlinx() })
    is GsonPrimitive -> when {
        isBoolean -> JsonPrimitive(asBoolean)
        isNumber -> JsonPrimitive(asNumber)
        else -> JsonPrimitive(asString)
    }
    else -> JsonNull
}
