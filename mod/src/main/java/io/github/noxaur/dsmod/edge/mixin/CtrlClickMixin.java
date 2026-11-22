package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.Hooks;
import io.github.noxaur.dsmod.edge.input.InputApproved;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** @keep On macOS the game turns a left press with Ctrl held into a right press. A release is left to the game, which turns
 * it into a right release only for a press it turned, so a switch flipped between the two cannot leave a button held. */
@InputApproved(by = "noxaur", hash = "0385679625f2")
@Mixin(MouseHandler.class)
abstract class CtrlClickMixin {
    @Inject(method = "simulateRightClick", at = @At("HEAD"), cancellable = true)
    private void dsmod$keep(MouseButtonInfo button, boolean pressed, CallbackInfoReturnable<MouseButtonInfo> cir) {
        if (pressed && Hooks.ctrlClicks()) cir.setReturnValue(button);
    }
}
