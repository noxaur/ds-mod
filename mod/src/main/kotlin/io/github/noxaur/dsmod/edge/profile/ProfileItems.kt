package io.github.noxaur.dsmod.edge.profile

import com.google.common.collect.ImmutableMultimap
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import io.github.noxaur.dsmod.core.game.Skulls
import io.github.noxaur.dsmod.core.profile.DrawnItem
import io.github.noxaur.dsmod.edge.customData
import io.github.noxaur.dsmod.edge.stackOf
import io.github.noxaur.dsmod.edge.toComponent
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.DyedItemColor
import net.minecraft.world.item.component.ItemAttributeModifiers
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.component.ResolvableProfile
import net.minecraft.world.item.component.TooltipDisplay
import java.util.UUID

fun DrawnItem.toStack(): ItemStack {
    val stack = stackOf(material, count.coerceIn(1, 99))
    stack.set(DataComponents.CUSTOM_NAME, name.toComponent())
    stack.set(DataComponents.LORE, ItemLore(lore.map { it.toComponent() }))
    stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, glint)
    stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
    stack.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay(false, linkedSetOf(DataComponents.DYED_COLOR)))
    itemModel?.let(Identifier::tryParse)?.let { stack.set(DataComponents.ITEM_MODEL, it) }
    color?.let { stack.set(DataComponents.DYED_COLOR, DyedItemColor(it)) }
    customData?.let(stack::customData)
    rarity?.let(::tooltipStyle)?.let { stack.set(DataComponents.TOOLTIP_STYLE, it) }
    skullTexture?.takeIf { stack.item == Items.PLAYER_HEAD && Skulls.onMojang(it) }?.let { texture ->
        val properties = PropertyMap(ImmutableMultimap.of("textures", Property("textures", texture)))
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(GameProfile(UUID.nameUUIDFromBytes(texture.toByteArray()), "", properties)))
    }
    return stack
}

private fun tooltipStyle(rarity: String): Identifier? {
    val style = Identifier.tryBuild("dragsim", rarity.lowercase()) ?: return null
    val sprite = style.withPath { "textures/gui/sprites/tooltip/${it}_background.png" }
    return style.takeIf { Minecraft.getInstance().resourceManager.getResource(sprite).isPresent }
}
