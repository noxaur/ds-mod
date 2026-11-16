package io.github.noxaur.dsmod.edge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.noxaur.dsmod.edge.PlayerModels;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player Model: the spin's facing, once the game has read the entity's own, then the size and tilt, after the game's
 * own turns and before its scale and the model's offset.
 */
@Mixin(AvatarRenderer.class)
abstract class PlayerShapeMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void dsmod$face(Avatar avatar, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        PlayerModels.INSTANCE.face(state);
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("TAIL"))
    private void dsmod$shape(AvatarRenderState state, PoseStack pose, float bodyRot, float scale, CallbackInfo ci) {
        PlayerModels.INSTANCE.shape(state, pose);
    }
}
