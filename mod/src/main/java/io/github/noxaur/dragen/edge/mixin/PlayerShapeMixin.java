package io.github.noxaur.dragen.edge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.noxaur.dragen.edge.PlayerModels;
import io.github.noxaur.dragen.edge.input.InputApproved;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** @keep The spin's facing is handed to the game's own turn after a screen such as the inventory has set a facing of its own.
 * The size and tilt come after the game's own turns and before its scale and the model's offset. */
@InputApproved(hash = "55186b2a8abc")
@Mixin(AvatarRenderer.class)
abstract class PlayerShapeMixin {
    @ModifyArg(
        method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;setupRotations(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V"),
        index = 2
    )
    private float dragen$face(LivingEntityRenderState state, PoseStack pose, float bodyRot, float scale) {
        return PlayerModels.INSTANCE.facing((AvatarRenderState) state, bodyRot);
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("TAIL"))
    private void dragen$shape(AvatarRenderState state, PoseStack pose, float bodyRot, float scale, CallbackInfo ci) {
        PlayerModels.INSTANCE.shape(state, pose);
    }
}
