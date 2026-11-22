package io.github.noxaur.dsmod.edge.capture

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
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ResolvableProfile

fun Component.toCaptureJson(): JsonObject = buildJsonObject {
    put("text", string)
    put("component", encode(ComponentSerialization.CODEC, this@toCaptureJson))
}

fun JsonObjectBuilder.position(x: Number, y: Number, z: Number) {
    put("x", x)
    put("y", y)
    put("z", z)
}

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

fun ResolvableProfile.toCaptureJson(): JsonElement = encode(ResolvableProfile.CODEC, this)
