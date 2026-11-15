package io.github.noxaur.dsmod.edge.profile

import com.google.common.collect.ImmutableMultimap
import com.google.gson.JsonParser
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import com.mojang.serialization.JsonOps
import io.github.noxaur.dsmod.core.game.Skulls
import io.github.noxaur.dsmod.core.profile.DrawnItem
import io.github.noxaur.dsmod.edge.toComponent
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.DyedItemColor
import net.minecraft.world.item.component.ItemAttributeModifiers
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.component.ResolvableProfile
import net.minecraft.world.item.component.TooltipDisplay
import java.util.UUID

/**
 * The item the backend drew, as an `ItemStack` the game draws and hovers like one in a real menu. The tooltip is the
 * backend's name and lore alone: the game's own lines for a material, such as a sword's attack damage, are hidden,
 * as the server hides them. A material the game lacks is drawn as a barrier, still with its name and lore. It carries
 * the server's data as a real item does, and the server's tooltip frame for its rarity, so the tooltip looks as in a
 * menu and another mod that reads or adds to a real item's tooltip does the same here.
 */
fun DrawnItem.toStack(): ItemStack {
    val item = Identifier.tryParse(material)?.let { BuiltInRegistries.ITEM.getOptional(it).orElse(null) } ?: Items.BARRIER
    val stack = ItemStack(item, count.coerceIn(1, 99))
    stack.set(DataComponents.CUSTOM_NAME, name.toComponent())
    stack.set(DataComponents.LORE, ItemLore(lore.map { it.toComponent() }))
    stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, glint)
    stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
    stack.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay(false, linkedSetOf(DataComponents.DYED_COLOR)))
    itemModel?.let(Identifier::tryParse)?.let { stack.set(DataComponents.ITEM_MODEL, it) }
    color?.let { stack.set(DataComponents.DYED_COLOR, DyedItemColor(it)) }
    customData?.let { data ->
        (JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, JsonParser.parseString(data.toString())) as? CompoundTag)?.let { stack.set(DataComponents.CUSTOM_DATA, CustomData.of(it)) }
    }
    rarity?.let(::tooltipStyle)?.let { stack.set(DataComponents.TOOLTIP_STYLE, it) }
    // The backend lets only Mojang's skins through, and the game is asked to fetch nothing that is not one either.
    skullTexture?.takeIf { item == Items.PLAYER_HEAD && Skulls.onMojang(it) }?.let { texture ->
        val properties = PropertyMap(ImmutableMultimap.of("textures", Property("textures", texture)))
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(GameProfile(UUID.nameUUIDFromBytes(texture.toByteArray()), "", properties)))
    }
    return stack
}

/**
 * The frame dragsim.co's resource pack gives a tooltip of [rarity], as `dragsim:legendary`, or null without the pack,
 * where the game would draw a missing texture for it.
 */
private fun tooltipStyle(rarity: String): Identifier? {
    val style = Identifier.tryBuild("dragsim", rarity.lowercase()) ?: return null
    val sprite = style.withPath { "textures/gui/sprites/tooltip/${it}_background.png" }
    return style.takeIf { Minecraft.getInstance().resourceManager.getResource(sprite).isPresent }
}
