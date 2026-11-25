package io.github.noxaur.dragen.edge.mixin;

import io.github.noxaur.dragen.edge.Hooks;
import io.github.noxaur.dragen.edge.input.InputApproved;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** @keep On macOS the game turns a left press with Ctrl held into a right press. A release is left to the game, which turns
 * it into a right release only for a press it turned, so a switch flipped between the two cannot leave a button held. */
@InputApproved(by = "noxaur", hash = "7b8de828ecec")
@Mixin(MouseHandler.class)
abstract class CtrlClickMixin {
    @Inject(method = "simulateRightClick", at = @At("HEAD"), cancellable = true)
    private void dragen$keep(MouseButtonInfo button, boolean pressed, CallbackInfoReturnable<MouseButtonInfo> cir) {
        if (pressed && Hooks.ctrlClicks()) cir.setReturnValue(button);
    }
}
