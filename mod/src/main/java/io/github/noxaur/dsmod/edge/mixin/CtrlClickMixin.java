package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.DsModClient;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ctrl Click: on macOS the game turns a left press with Ctrl held into a right press, as macOS does. The press stays
 * a left press with Ctrl, as on every other system. A release is left to the game, which turns it into a right
 * release only for a press it turned, so a switch flipped between the two cannot leave a button held.
 */
@Mixin(MouseHandler.class)
abstract class CtrlClickMixin {
    @Inject(method = "simulateRightClick", at = @At("HEAD"), cancellable = true)
    private void dsmod$keep(MouseButtonInfo button, boolean pressed, CallbackInfoReturnable<MouseButtonInfo> cir) {
        if (pressed && DsModClient.INSTANCE.getActive() && DsModClient.INSTANCE.getSettings().general.ctrlClick) cir.setReturnValue(button);
    }
}
