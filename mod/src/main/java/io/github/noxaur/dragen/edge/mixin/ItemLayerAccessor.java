package io.github.noxaur.dragen.edge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.noxaur.dragen.edge.input.InputApproved;
import java.util.List;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@InputApproved(hash = "786ecfc38e0e")
@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState")
public interface ItemLayerAccessor {
    @Accessor("quads")
    List<BakedQuad> dragen$quads();

    @Invoker("applyTransform")
    void dragen$applyTransform(PoseStack.Pose pose);
}
