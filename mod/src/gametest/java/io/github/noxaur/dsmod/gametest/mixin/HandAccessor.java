package io.github.noxaur.dsmod.gametest.mixin;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The item the hand is drawn with, which lags the player's own while it is swapped, and how far it is raised, this
 * tick and the last, from 0 to 1. A test waits for these to settle instead of a fixed time.
 */
@Mixin(ItemInHandRenderer.class)
public interface HandAccessor {
    @Accessor
    ItemStack getMainHandItem();

    @Accessor
    float getMainHandHeight();

    @Accessor
    float getOMainHandHeight();
}
