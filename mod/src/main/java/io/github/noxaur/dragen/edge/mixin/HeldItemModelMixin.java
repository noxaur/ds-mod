package io.github.noxaur.dragen.edge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.noxaur.dragen.edge.HeldItems;
import io.github.noxaur.dragen.edge.input.InputApproved;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@InputApproved(hash = "7a975bca7d41")
@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState")
abstract class HeldItemModelMixin {
    @Shadow
    private SpecialModelRenderer<Object> specialRenderer;

    @WrapOperation(method = "applyTransform", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/cuboid/ItemTransform;apply(ZLcom/mojang/blaze3d/vertex/PoseStack$Pose;)V"))
    private void dragen$hold(ItemTransform transform, boolean left, PoseStack.Pose pose, Operation<Void> original) {
        boolean flip = specialRenderer == null && HeldItems.INSTANCE.getFlipping();
        if (flip) HeldItems.INSTANCE.beforeHold(pose);
        if (!HeldItems.INSTANCE.hold(transform, left, pose)) original.call(transform, left, pose);
        if (flip) HeldItems.INSTANCE.flip(pose);
    }

    @WrapOperation(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V"))
    private void dragen$wind(SubmitNodeCollector collector, PoseStack pose, ItemDisplayContext context, int light, int overlay, int outline, int[] tints, List<BakedQuad> quads,
                            ItemStackRenderState.FoilType foil, Operation<Void> original) {
        original.call(collector, pose, context, light, overlay, outline, tints, HeldItems.INSTANCE.getFlipping() ? HeldItems.INSTANCE.mirrored(quads) : quads, foil);
    }
}
