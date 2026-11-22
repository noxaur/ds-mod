package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.PlayerModels;
import io.github.noxaur.dsmod.edge.input.InputApproved;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** @keep The player's model and each piece of armor are a humanoid model posed from the same state, and the player's own
 * `setupAnim` ends in this one, so the pose has the last word on every one of them. */
@InputApproved(hash = "2dacd2ed0a3a")
@Mixin(HumanoidModel.class)
abstract class PlayerPoseMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void dsmod$pose(HumanoidRenderState state, CallbackInfo ci) {
        if (state instanceof AvatarRenderState avatar) PlayerModels.INSTANCE.pose((HumanoidModel<?>) (Object) this, avatar);
    }
}
