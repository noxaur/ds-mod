package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.game.Item
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
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

/** Walks the components and the NBT directly, because the codecs are slow and this runs on the client thread. */
fun ItemStack.toItem() = Item(
    BuiltInRegistries.ITEM.getKey(item).toString(),
    count,
    get(DataComponents.CUSTOM_NAME)?.toText(),
    get(DataComponents.LORE)?.lines()?.map { it.toText() }.orEmpty(),
    get(DataComponents.CUSTOM_DATA)?.copyTag()?.toJson() ?: JsonObject(emptyMap()),
)

/** A plain stack of [Item.id], for an item no stack was read for. It lacks what an [Item] leaves out, such as a head's skin. */
fun Item.plainStack() = ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)), count)

private fun CompoundTag.toJson() = JsonObject(entrySet().associate { (key, tag) -> key to tag.toJson() })

private fun Tag.toJson(): JsonElement = when (this) {
    is CompoundTag -> toJson()
    is CollectionTag -> JsonArray(map { it.toJson() })
    is StringTag -> JsonPrimitive(value())
    is FloatTag, is DoubleTag -> JsonPrimitive((this as NumericTag).doubleValue())
    is NumericTag -> JsonPrimitive(longValue())
    else -> JsonPrimitive(toString())
}
