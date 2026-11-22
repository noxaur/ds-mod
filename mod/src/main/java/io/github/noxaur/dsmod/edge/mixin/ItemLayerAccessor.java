package io.github.noxaur.dsmod.edge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.noxaur.dsmod.edge.input.InputApproved;
import java.util.List;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@InputApproved(hash = "44bd4cac718b")
@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState")
public interface ItemLayerAccessor {
    @Accessor("quads")
    List<BakedQuad> dsmod$quads();

    @Invoker("applyTransform")
    void dsmod$applyTransform(PoseStack.Pose pose);
}
