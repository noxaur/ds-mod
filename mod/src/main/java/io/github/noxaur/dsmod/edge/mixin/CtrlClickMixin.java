package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.core.feature.general.CtrlClick;
import io.github.noxaur.dsmod.edge.DsModClient;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** @keep On macOS the game turns a left press with Ctrl held into a right press. A release is left to the game, which turns
 * it into a right release only for a press it turned, so a switch flipped between the two cannot leave a button held. */
@Mixin(MouseHandler.class)
abstract class CtrlClickMixin {
    @Inject(method = "simulateRightClick", at = @At("HEAD"), cancellable = true)
    private void dsmod$keep(MouseButtonInfo button, boolean pressed, CallbackInfoReturnable<MouseButtonInfo> cir) {
        if (pressed && DsModClient.INSTANCE.runs(CtrlClick.class)) cir.setReturnValue(button);
    }
}
