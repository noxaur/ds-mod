package io.github.noxaur.dsmod.edge

import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.MapMaker
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import com.mojang.serialization.JsonOps
import com.google.gson.JsonParser
import io.github.noxaur.dsmod.core.container.Filler
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
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.NumericTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.DyedItemColor
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.component.ResolvableProfile
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val ids = ConcurrentHashMap<net.minecraft.world.item.Item, String>()

val ItemStack.id: String get() = ids.computeIfAbsent(item) { BuiltInRegistries.ITEM.getKey(it).toString() }

fun stackOf(id: String, count: Int = 1): ItemStack = ItemStack(itemOf(id) ?: Items.BARRIER, count)

fun isItem(id: String) = itemOf(id) != null

private fun itemOf(id: String) = Identifier.tryParse(id)?.let { BuiltInRegistries.ITEM.getOptional(it).orElse(null) }

val ItemStack.plainName: String? get() = get(DataComponents.CUSTOM_NAME)?.string

val ItemStack.filler get() = Filler.of(id, plainName)

val ItemStack.itemId: String? get() = skyBlockData?.itemId

val ItemStack.itemUuid: String? get() = skyBlockData?.uuid

val ItemStack.skinId: String? get() = skyBlockData?.skinId

private class SkyBlockData(val itemId: String?, val uuid: String?, val skinId: String?)

private val skyBlockDataByTag: MutableMap<CustomData, SkyBlockData> = MapMaker().weakKeys().makeMap()

private val ItemStack.skyBlockData: SkyBlockData?
    get() {
        val data = get(DataComponents.CUSTOM_DATA) ?: return null
        return skyBlockDataByTag.getOrPut(data) {
            val tag = data.copyTag()
            val item = tag.getCompoundOrEmpty("item_data")
            SkyBlockData(item.string("item_id"), item.string("uuid"), tag.getCompoundOrEmpty("extra").getCompoundOrEmpty("item_skin").string("skinId"))
        }
    }

private fun CompoundTag.string(key: String) = getStringOr(key, "").ifEmpty { null }

/** @keep The codecs are slow and this runs on the client thread, so it walks the components and the NBT directly. */
fun ItemStack.toItem(): Item {
    val key = id
    return Item(
        key,
        count,
        get(DataComponents.CUSTOM_NAME)?.toText(),
        get(DataComponents.LORE)?.lines()?.map { it.toText() }.orEmpty(),
        get(DataComponents.CUSTOM_DATA)?.copyTag()?.toJson() ?: JsonObject(emptyMap()),
        skullTexture()?.let(::skin),
        get(DataComponents.DYED_COLOR)?.rgb(),
        get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE),
        get(DataComponents.TOOLTIP_STYLE)?.toString(),
        hasFoil(),
        get(DataComponents.ITEM_MODEL)?.takeIf { it != BuiltInRegistries.ITEM.getKey(item) }?.toString(),
    )
}

fun ItemStack.skullTexture(): String? =
    get(DataComponents.PROFILE)?.partialProfile()?.properties()?.get(TEXTURES)?.firstOrNull()?.value()

/** @keep A head's skin goes back without the signature the server sent, which the game accepts for a head. dragsim.co strips
 * the vanilla attributes from its items and writes their stats in the lore, so the stack has none, or its tooltip
 * would add armor. */
fun Item.toStack(): ItemStack {
    val stack = stackOf(id, count)
    stack.remove(DataComponents.ATTRIBUTE_MODIFIERS)
    name?.let { stack.set(DataComponents.CUSTOM_NAME, it.toComponent()) }
    if (lore.isNotEmpty()) stack.set(DataComponents.LORE, ItemLore(lore.map { it.toComponent() }))
    skin?.let {
        val properties = PropertyMap(ImmutableMultimap.of(TEXTURES, Property(TEXTURES, Skulls.property(it))))
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(GameProfile(UUID.nameUUIDFromBytes(it.toByteArray()), "", properties)))
    }
    dye?.let { stack.set(DataComponents.DYED_COLOR, DyedItemColor(it)) }
    glint?.let { stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, it) }
    style?.let { stack.set(DataComponents.TOOLTIP_STYLE, Identifier.parse(it)) }
    if (data.isNotEmpty()) stack.customData(data)
    return stack
}

/** @keep The server's item data goes back on a rebuilt stack, because other mods read a tooltip's item data, and a stack
 * without it would show a bare tooltip where a menu shows theirs. */
fun ItemStack.customData(data: JsonObject) {
    (JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, JsonParser.parseString(data.toString())) as? CompoundTag)?.let { set(DataComponents.CUSTOM_DATA, CustomData.of(it)) }
}

private const val TEXTURES = "textures"
private val SKIN = Regex("""textures\.minecraft\.net/texture/([0-9a-f]+)""")

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
