package io.github.noxaur.dragen.edge.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.noxaur.dragen.edge.HeldItems;
import io.github.noxaur.dragen.edge.input.InputApproved;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@InputApproved(hash = "5b8e64ac6946")
@Mixin(ItemInHandRenderer.class)
abstract class HeldItemMixin {
    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
    private void dragen$place(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack, ItemStack stack,
                             float inverseArmHeight, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        HeldItems.INSTANCE.place(pose, dragen$armOf(player, hand), stack);
    }

    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
    private void dragen$draw(ItemInHandRenderer self, LivingEntity mob, ItemStack stack, ItemDisplayContext context, PoseStack pose, SubmitNodeCollector collector,
                            int light, Operation<Void> original) {
        HeldItems.INSTANCE.turnAndSize(pose, context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
        HeldItems.setDrawing(true);
        try {
            original.call(self, mob, stack, context, pose, collector, light);
        } finally {
            HeldItems.setDrawing(false);
        }
    }

    @WrapOperation(method = "submitHandsWithItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemModelResolver;swapAnimationScale(Lnet/minecraft/world/item/ItemStack;)F"))
    private float dragen$swapDepth(ItemModelResolver resolver, ItemStack stack, Operation<Float> original) {
        return HeldItems.INSTANCE.swapDepth(stack, original.call(resolver, stack));
    }

    private static HumanoidArm dragen$armOf(AbstractClientPlayer player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
    }

    @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
    private void dragen$swing(float attack, PoseStack pose, int invert, HumanoidArm arm, CallbackInfo ci) {
        if (HeldItems.INSTANCE.swing(pose, arm, attack, false)) ci.cancel();
    }

    /** @keep A spear swings by its own stab, which never reaches `swingArm`. */
    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/effects/SpearAnimations;firstPersonAttack(FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V"))
    private void dragen$stab(float attack, PoseStack pose, int invert, HumanoidArm arm, Operation<Void> original) {
        if (!HeldItems.INSTANCE.swing(pose, arm, attack, true)) original.call(attack, pose, invert, arm);
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getItemSwapScale(F)F"))
    private float dragen$swapScale(float scale) {
        return HeldItems.INSTANCE.swapScale(scale);
    }

    @ModifyReturnValue(method = "shouldInstantlyReplaceVisibleItem", at = @At("RETURN"))
    private boolean dragen$replaceAtOnce(boolean vanilla, ItemStack shown, ItemStack next) {
        return HeldItems.INSTANCE.replaceAtOnce(vanilla, shown, next);
    }

    @Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true)
    private void dragen$dipOnUse(InteractionHand hand, CallbackInfo ci) {
        if (!HeldItems.INSTANCE.dipOnUse()) ci.cancel();
    }
}
