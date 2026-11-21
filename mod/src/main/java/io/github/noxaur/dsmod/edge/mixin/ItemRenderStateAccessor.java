package io.github.noxaur.dsmod.edge.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemStackRenderState.class)
public interface ItemRenderStateAccessor {
    @Accessor("activeLayerCount")
    int dsmod$activeLayerCount();

    @Accessor("layers")
    ItemStackRenderState.LayerRenderState[] dsmod$layers();
}
