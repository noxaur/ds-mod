package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.Skulls
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CollectionTag
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.FloatTag
import net.minecraft.nbt.NumericTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.world.item.ItemStack

private const val RGB = (1 shl 24) - 1

/** Walks the components and the NBT directly, because the codecs are slow and this runs on the client thread. */
fun ItemStack.toItem(): Item {
    val id = BuiltInRegistries.ITEM.getKey(item).toString()
    return Item(
        id,
        count,
        get(DataComponents.CUSTOM_NAME)?.toText(),
        get(DataComponents.LORE)?.lines()?.map { it.toText() }.orEmpty(),
        get(DataComponents.CUSTOM_DATA)?.copyTag()?.toJson() ?: JsonObject(emptyMap()),
        hasFoil(),
        get(DataComponents.ITEM_MODEL)?.toString()?.takeIf { it != id },
        skullTexture()?.let(Skulls::withoutPlayer),
        get(DataComponents.DYED_COLOR)?.rgb()?.and(RGB),
    )
}

/** The `textures` property of a player head's profile, as the server wrote it. */
fun ItemStack.skullTexture(): String? =
    get(DataComponents.PROFILE)?.partialProfile()?.properties()?.get("textures")?.firstOrNull()?.value()

private fun CompoundTag.toJson() = JsonObject(entrySet().associate { (key, tag) -> key to tag.toJson() })

private fun Tag.toJson(): JsonElement = when (this) {
    is CompoundTag -> toJson()
    is CollectionTag -> JsonArray(map { it.toJson() })
    is StringTag -> JsonPrimitive(value())
    is FloatTag, is DoubleTag -> JsonPrimitive((this as NumericTag).doubleValue())
    is NumericTag -> JsonPrimitive(longValue())
    else -> JsonPrimitive(toString())
}
