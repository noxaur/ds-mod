package io.github.noxaur.dsmod.edge.profile

import com.google.common.collect.ImmutableMultimap
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import io.github.noxaur.dsmod.core.game.Skulls
import io.github.noxaur.dsmod.core.profile.DrawnItem
import io.github.noxaur.dsmod.edge.toComponent
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.DyedItemColor
import net.minecraft.world.item.component.ItemAttributeModifiers
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.component.ResolvableProfile
import net.minecraft.world.item.component.TooltipDisplay
import java.util.UUID

/**
 * The item the backend drew, as an `ItemStack` the game draws and hovers like one in a real menu. The tooltip is the
 * backend's name and lore alone: the game's own lines for a material, such as a sword's attack damage, are hidden,
 * as the server hides them. A material the game lacks is drawn as a barrier, still with its name and lore.
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
    // The backend lets only Mojang's skins through, and the game is asked to fetch nothing that is not one either.
    skullTexture?.takeIf { item == Items.PLAYER_HEAD && Skulls.onMojang(it) }?.let { texture ->
        val properties = PropertyMap(ImmutableMultimap.of("textures", Property("textures", texture)))
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(GameProfile(UUID.nameUUIDFromBytes(texture.toByteArray()), "", properties)))
    }
    return stack
}
