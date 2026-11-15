package io.github.noxaur.dsmod.edge

import com.google.common.collect.ImmutableMultimap
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
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
import net.minecraft.world.item.component.DyedItemColor
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.component.ResolvableProfile
import java.util.Base64
import java.util.UUID

/** Walks the components and the NBT directly, because the codecs are slow and this runs on the client thread. */
fun ItemStack.toItem() = Item(
    BuiltInRegistries.ITEM.getKey(item).toString(),
    count,
    get(DataComponents.CUSTOM_NAME)?.toText(),
    get(DataComponents.LORE)?.lines()?.map { it.toText() }.orEmpty(),
    get(DataComponents.CUSTOM_DATA)?.copyTag()?.toJson() ?: JsonObject(emptyMap()),
    get(DataComponents.PROFILE)?.partialProfile()?.properties()?.get(TEXTURES)?.firstOrNull()?.let { skin(it.value()) },
    get(DataComponents.DYED_COLOR)?.rgb(),
    get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE),
    get(DataComponents.TOOLTIP_STYLE)?.toString(),
)

/**
 * A stack that draws as the [Item] and shows its tooltip. A head's skin goes back without the signature the server
 * sent, which the game accepts for a head, as it does for a head a command gives. dragsim.co strips the vanilla
 * attributes from its items and writes their stats in the lore, so the stack has none, or its tooltip would add armor.
 */
fun Item.toStack(): ItemStack {
    val stack = ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)), count)
    stack.remove(DataComponents.ATTRIBUTE_MODIFIERS)
    name?.let { stack.set(DataComponents.CUSTOM_NAME, it.toComponent()) }
    if (lore.isNotEmpty()) stack.set(DataComponents.LORE, ItemLore(lore.map { it.toComponent() }))
    skin?.let {
        val value = Base64.getEncoder().encodeToString("{\"textures\":{\"SKIN\":{\"url\":\"$SKINS$it\"}}}".toByteArray())
        val properties = PropertyMap(ImmutableMultimap.of(TEXTURES, Property(TEXTURES, value)))
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(GameProfile(UUID.nameUUIDFromBytes(it.toByteArray()), "", properties)))
    }
    dye?.let { stack.set(DataComponents.DYED_COLOR, DyedItemColor(it)) }
    glint?.let { stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, it) }
    style?.let { stack.set(DataComponents.TOOLTIP_STYLE, Identifier.parse(it)) }
    return stack
}

private const val TEXTURES = "textures"
private const val SKINS = "http://textures.minecraft.net/texture/"
private val SKIN = Regex("""textures\.minecraft\.net/texture/([0-9a-f]+)""")

/** The skin's part of a `textures` property's value, which is base64 of JSON naming the skin's address. */
private fun skin(value: String) = runCatching { SKIN.find(String(Base64.getDecoder().decode(value)))?.groupValues?.get(1) }.getOrNull()

private fun CompoundTag.toJson() = JsonObject(entrySet().associate { (key, tag) -> key to tag.toJson() })

private fun Tag.toJson(): JsonElement = when (this) {
    is CompoundTag -> toJson()
    is CollectionTag -> JsonArray(map { it.toJson() })
    is StringTag -> JsonPrimitive(value())
    is FloatTag, is DoubleTag -> JsonPrimitive((this as NumericTag).doubleValue())
    is NumericTag -> JsonPrimitive(longValue())
    else -> JsonPrimitive(toString())
}
