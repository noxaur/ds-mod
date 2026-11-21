package io.github.noxaur.dsmod.edge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState")
public interface ItemLayerAccessor {
    @Accessor("quads")
    List<BakedQuad> dsmod$quads();

    @Invoker("applyTransform")
    void dsmod$applyTransform(PoseStack.Pose pose);
}
