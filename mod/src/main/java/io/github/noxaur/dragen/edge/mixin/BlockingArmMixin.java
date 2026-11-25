package io.github.noxaur.dragen.edge.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.noxaur.dragen.edge.HeldItems;
import io.github.noxaur.dragen.edge.input.InputApproved;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@InputApproved(hash = "4740b3d1697b")
@Mixin(AvatarRenderer.class)
abstract class BlockingArmMixin {
    @ModifyReturnValue(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/entity/HumanoidArm;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;", at = @At("RETURN"))
    private static HumanoidModel.ArmPose dragen$block(HumanoidModel.ArmPose pose, Avatar avatar, HumanoidArm arm) {
        return HeldItems.INSTANCE.blocks(avatar, arm) ? HumanoidModel.ArmPose.BLOCK : pose;
    }
}
