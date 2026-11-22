package io.github.noxaur.dsmod.gametest.mixin;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemInHandRenderer.class)
public interface HandAccessor {
    @Accessor
    ItemStack getMainHandItem();

    @Accessor
    float getMainHandHeight();

    @Accessor
    float getOMainHandHeight();

    @Accessor
    ItemStack getOffHandItem();

    @Accessor
    float getOffHandHeight();

    @Accessor
    float getOOffHandHeight();
}
